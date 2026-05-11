package ac.uk.sussex.kn253.resources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import ac.uk.sussex.kn253.repository.*;
import ac.uk.sussex.kn253.services.SummaryFormattingService;
import ac.uk.sussex.kn253.services.ai.FileSummarisationPipelineService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@jakarta.ws.rs.Path("/api/summary{operation: (/.*)?}")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class SummaryResource {

    private static final int MAX_SUMMARY_FILES = 32;
    private static final int MAX_SUMMARY_FILE_CHARS = 24_000;

    @Inject
    FileSummarisationPipelineService fileSummarisationPipelineService;

    @Inject
    SummaryFormattingService summaryFormattingService;

    public record FolderSummaryResponse(
            String folderPath,
            String summary,
            int analysedFiles,
            int skippedFiles,
            String updatedAt,
            String fallbackReason,
            boolean persisted) {
    }

    public record FolderSubsummaryItem(
            String targetPath,
            String purpose,
            String updatedAt) {
    }

    public record FolderSubsummaryResponse(
            String folderPath,
            int count,
            List<FolderSubsummaryItem> items) {
    }

    public record FolderSummaryStatusResponse(
            String folderPath,
            boolean exists,
            String updatedAt) {
    }

    public record SummaryRequest(
            long projectId,
            String entryUuid) {
    }

    FolderSummaryResponse folder(final SummaryRequest request) {
        if (request == null || request.projectId() <= 0) {
            throw new BadRequestException("projectId is required");
        }
        if (request.entryUuid() == null || request.entryUuid().isBlank()) {
            throw new BadRequestException("entryUuid is required");
        }
        final long id = request.projectId();
        final String entryUuid = request.entryUuid();

        final ProjectFolder project = findProjectOrThrow(id);
        final Path root = Path.of(project.getDirectory()).toAbsolutePath().normalize();
        final Path folder = resolvePathByUuid(root, entryUuid);

        if (folder == null || !Files.exists(folder) || !Files.isDirectory(folder)) {
            throw new NotFoundException("Folder not found");
        }

        final String folderPath = normalizeRelativePath(root, folder);
        final FolderAnalysisInput analysisInput = buildFolderAnalysisInput(project, root, folder, folderPath);
        final FileSummarisationPipelineService.FolderSummaryResult folderSummaryResult = fileSummarisationPipelineService
                .summariseFolderAndStoreWithMetadata(
                        project,
                        folderPath,
                        analysisInput.promptInput());
        final StructuredSummary summary = folderSummaryResult.summary();

        final String markdown = buildFolderSummaryMarkdown(folderPath, summary, analysisInput.analysedFiles(),
                analysisInput.skippedFiles());

        return new FolderSummaryResponse(
                folderPath,
                markdown,
                analysisInput.analysedFiles(),
                analysisInput.skippedFiles(),
                summary.getUpdatedAt() == null ? null : summary.getUpdatedAt().toString(),
                folderSummaryResult.fallbackReason(),
                true);
    }

    FolderSubsummaryResponse file(final SummaryRequest request) {
        if (request == null || request.projectId() <= 0) {
            throw new BadRequestException("projectId is required");
        }
        if (request.entryUuid() == null || request.entryUuid().isBlank()) {
            throw new BadRequestException("entryUuid is required");
        }
        final long id = request.projectId();
        final String entryUuid = request.entryUuid();

        final ProjectFolder project = findProjectOrThrow(id);
        final Path root = Path.of(project.getDirectory()).toAbsolutePath().normalize();
        final Path folder = resolvePathByUuid(root, entryUuid);

        if (folder == null || !Files.exists(folder) || !Files.isDirectory(folder)) {
            throw new NotFoundException("Folder not found");
        }

        final String folderPath = normalizeRelativePath(root, folder);
        final String likePattern = ".".equals(folderPath) ? "%" : folderPath + "/%";

        final List<StructuredSummary> summaries = StructuredSummary.find(
                "project = ?1 and summaryType = ?2 and targetPath like ?3 order by updatedAt desc",
                project,
                SummaryType.FILE,
                likePattern).list();

        final List<FolderSubsummaryItem> items = summaries.stream()
                .map(summary -> new FolderSubsummaryItem(
                        summary.getTargetPath(),
                        summaryFormattingService.safeText(summary.getPurpose()),
                        summary.getUpdatedAt() == null ? null : summary.getUpdatedAt().toString()))
                .toList();

        return new FolderSubsummaryResponse(folderPath, items.size(), items);
    }

    FolderSummaryStatusResponse status(final SummaryRequest request) {
        if (request == null || request.projectId() <= 0) {
            throw new BadRequestException("projectId is required");
        }
        if (request.entryUuid() == null || request.entryUuid().isBlank()) {
            throw new BadRequestException("entryUuid is required");
        }
        final long id = request.projectId();
        final String entryUuid = request.entryUuid();

        final ProjectFolder project = findProjectOrThrow(id);
        final Path root = Path.of(project.getDirectory()).toAbsolutePath().normalize();
        final Path folder = resolvePathByUuid(root, entryUuid);

        if (folder == null || !Files.exists(folder) || !Files.isDirectory(folder)) {
            throw new NotFoundException("Folder not found");
        }

        final String folderPath = normalizeRelativePath(root, folder);
        final StructuredSummary summary = StructuredSummary.findByProjectTypeAndPath(
                project,
                SummaryType.FOLDER,
                folderPath);

        return new FolderSummaryStatusResponse(
                folderPath,
                summary != null,
                summary == null || summary.getUpdatedAt() == null ? null : summary.getUpdatedAt().toString());
    }

    @PUT
    @Transactional
    public Object put(
            @PathParam("operation") final String operation,
            final SummaryRequest request) {
        final String normalized = normalizeOperation(operation);
        return switch (normalized) {
            case "/folder" -> folder(request);
            case "/analyze" -> folder(request);
            case "/file" -> file(request);
            case "/status" -> status(request);
            default -> throw new NotFoundException("Unsupported summary operation");
        };
    }

    private String normalizeOperation(final String operation) {
        if (operation == null || operation.isBlank()) {
            return "";
        }
        final String trimmed = operation.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private ProjectFolder findProjectOrThrow(final long id) {
        final ProjectFolder project = ProjectFolder.<ProjectFolder>findById(id);
        if (project == null) {
            throw new NotFoundException("Project not found");
        }
        return project;
    }

    private Path resolvePathByUuid(final Path root, final String entryUuid) {
        if (entryUuid == null || entryUuid.isBlank()) {
            return root;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> uuidForPath(path).equals(entryUuid))
                    .findFirst()
                    .orElse(null);
        } catch (final Exception e) {
            return null;
        }
    }

    private String uuidForPath(final Path path) {
        final String normalized = path.toAbsolutePath().normalize().toString();
        return UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private FolderAnalysisInput buildFolderAnalysisInput(
            final ProjectFolder project,
            final Path root,
            final Path folder,
            final String folderPath) {
        final List<Path> candidates;
        try (Stream<Path> paths = Files.walk(folder)) {
            candidates = paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString().toLowerCase()))
                    .limit(MAX_SUMMARY_FILES)
                    .toList();
        } catch (final Exception e) {
            throw new InternalServerErrorException("Failed to inspect folder");
        }

        final List<String> sections = new ArrayList<>();
        int analysedFiles = 0;
        int skippedFiles = 0;

        for (final Path file : candidates) {
            if (!isSummarisableFile(file)) {
                skippedFiles += 1;
                continue;
            }

            final String fileContents;
            try {
                fileContents = readFileForSummary(file);
            } catch (final Exception e) {
                skippedFiles += 1;
                continue;
            }

            if (fileContents.isBlank()) {
                skippedFiles += 1;
                continue;
            }

            final String relativeFilePath = normalizeRelativePath(root, file);
            final StructuredSummary fileSummary = fileSummarisationPipelineService.summariseFileAndStore(
                    project,
                    relativeFilePath,
                    fileContents);
            sections.add(formatFileSummary(relativeFilePath, fileSummary));
            analysedFiles += 1;
        }

        final String promptInput = sections.isEmpty()
                ? "Folder path: " + folderPath + "\nNo analysable text files were found in this folder."
                : String.join("\n\n", sections);

        return new FolderAnalysisInput(promptInput, analysedFiles, skippedFiles);
    }

    private String formatFileSummary(final String relativeFilePath, final StructuredSummary fileSummary) {
        final String purpose = summaryFormattingService.safeText(fileSummary.getPurpose());
        final String keyComponents = summaryFormattingService.safeText(fileSummary.getKeyComponentsJson());
        final String dependencies = summaryFormattingService.safeText(fileSummary.getDependenciesJson());

        return "File: " + relativeFilePath + "\n"
                + "Purpose: " + purpose + "\n"
                + "Key Components JSON: " + keyComponents + "\n"
                + "Dependencies JSON: " + dependencies;
    }

    private String buildFolderSummaryMarkdown(
            final String folderPath,
            final StructuredSummary summary,
            final int analysedFiles,
            final int skippedFiles) {
        final String purpose = summaryFormattingService.safeText(summary.getPurpose());
        final String keyComponents = summaryFormattingService.safeText(summary.getKeyComponentsJson());
        final String dependencies = summaryFormattingService.safeText(summary.getDependenciesJson());

        return String.join("\n",
                "# Folder Summary",
                "",
                "Path: " + folderPath,
                "",
                "- Analysed files: " + analysedFiles,
                "- Skipped files: " + skippedFiles,
                "",
                "## Purpose",
                purpose,
                "",
                "## Key Components",
                "```json",
                keyComponents,
                "```",
                "",
                "## Dependencies",
                "```json",
                dependencies,
                "```");
    }

    private boolean isSummarisableFile(final Path file) {
        final String mimeType = detectMimeType(file);
        if (isImageMimeType(mimeType) || isPdfMimeType(mimeType, file)) {
            return false;
        }
        final String lowered = (mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT));
        if (lowered.startsWith("text/") || lowered.contains("json") || lowered.contains("xml")
                || lowered.contains("yaml") || lowered.contains("javascript")
                || lowered.contains("shell") || lowered.contains("x-sh")) {
            return true;
        }
        final String fileName = file.getFileName() == null
                ? ""
                : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".md") || fileName.endsWith(".markdown") || fileName.endsWith(".mdx")
                || fileName.endsWith(".java") || fileName.endsWith(".ts") || fileName.endsWith(".tsx")
                || fileName.endsWith(".js") || fileName.endsWith(".jsx") || fileName.endsWith(".py")
                || fileName.endsWith(".json") || fileName.endsWith(".yaml") || fileName.endsWith(".yml")
                || fileName.endsWith(".txt") || fileName.endsWith(".properties") || fileName.endsWith(".xml")
                || fileName.endsWith(".sql") || fileName.endsWith(".sh") || fileName.endsWith(".bash")
                || fileName.endsWith(".zsh");
    }

    private String detectMimeType(final Path path) {
        try {
            final String detected = Files.probeContentType(path);
            if (detected != null && !detected.isBlank()) {
                return detected;
            }
        } catch (final Exception ignored) {
        }
        return "text/plain";
    }

    private boolean isPdfMimeType(final String mimeType, final Path path) {
        return "application/pdf".equalsIgnoreCase(mimeType)
                || path.getFileName().toString().toLowerCase().endsWith(".pdf");
    }

    private boolean isImageMimeType(final String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("image/");
    }

    private String readFileForSummary(final Path file) throws Exception {
        final String raw = Files.readString(file, StandardCharsets.UTF_8);
        if (raw.length() <= MAX_SUMMARY_FILE_CHARS) {
            return raw;
        }
        return raw.substring(0, MAX_SUMMARY_FILE_CHARS);
    }

    private String normalizeRelativePath(final Path root, final Path path) {
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path normalizedPath = path.toAbsolutePath().normalize();
        if (normalizedRoot.equals(normalizedPath)) {
            return ".";
        }
        return normalizedRoot.relativize(normalizedPath).toString().replace('\\', '/');
    }

    private record FolderAnalysisInput(String promptInput, int analysedFiles, int skippedFiles) {
    }
}