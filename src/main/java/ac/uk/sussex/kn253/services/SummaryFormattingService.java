package ac.uk.sussex.kn253.services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.repository.StructuredSummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Formats and builds markdown summaries for folder and file analysis output.
 */
@ApplicationScoped
public class SummaryFormattingService {

    private static final int MAX_SUMMARY_FILES = 32;
    private static final int MAX_SUMMARY_FILE_CHARS = 24_000;
    private static final Pattern EVIDENCE_HEADER_PATTERN = Pattern
            .compile("(?m)^===.*?===\\s*$|^---\\s*File:.*?---\\s*$");
    private static final Pattern EVIDENCE_NOTICE_PATTERN = Pattern
            .compile("(?i)\\(evidence only\\)|\\.\\.\\.\\(truncated\\)");

    @Inject
    FileTypeDetectionService fileTypeDetectionService;

    @Inject
    PathResolutionService pathResolutionService;

    public String buildFolderSummaryMarkdown(
            final String folderPath,
            final StructuredSummary summary,
            final int analysedFiles,
            final int skippedFiles) {
        final String purpose = safeText(summary.getPurpose());
        final String keyComponents = safeText(summary.getKeyComponentsJson());
        final String dependencies = safeText(summary.getDependenciesJson());

        final StringBuilder sb = new StringBuilder();
        sb.append("# Folder Summary\n\n");
        sb.append("Path: ").append(folderPath).append("\n\n");
        sb.append("## Purpose\n").append(purpose).append("\n\n");
        sb.append("## Key Components\n").append(keyComponents).append("\n\n");
        sb.append("## Dependencies\n").append(dependencies).append("\n\n");
        sb.append("**Analysis:** ").append(analysedFiles).append(" file(s) analysed");
        if (skippedFiles > 0) {
            sb.append(", ").append(skippedFiles).append(" skipped");
        }
        sb.append(".");
        return sb.toString();
    }

    public String formatFileSummary(final String relativeFilePath, final StructuredSummary fileSummary) {
        final StringBuilder sb = new StringBuilder();
        sb.append("- **").append(relativeFilePath).append("**: ");
        sb.append(safeText(fileSummary.getPurpose()));
        return sb.toString();
    }

    public String safeText(final String value) {
        if (value == null || value.isBlank()) {
            return "(no description)";
        }
        String cleaned = EVIDENCE_HEADER_PATTERN.matcher(value).replaceAll("");
        cleaned = EVIDENCE_NOTICE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("(?m)^[\\t ]+$", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\\n\\n").trim();
        return cleaned.isBlank() ? "(no description)" : cleaned;
    }

    public String readFileForSummary(final Path file) throws Exception {
        final String content = Files.readString(file, StandardCharsets.UTF_8);
        return content.length() > MAX_SUMMARY_FILE_CHARS
                ? content.substring(0, MAX_SUMMARY_FILE_CHARS)
                : content;
    }

    public record FolderAnalysisInput(String promptInput, int analysedFiles, int skippedFiles) {
    }

    public FolderAnalysisInput buildFolderAnalysisInput(
            final ProjectFolder project,
            final Path root,
            final Path folder,
            final String folderPath) {
        final List<Path> allPaths = pathResolutionService.listDirectoryEntries(folder);
        final List<Path> filePaths = allPaths.stream()
                .filter(p -> !Files.isDirectory(p) && fileTypeDetectionService.isSummarisableFile(p))
                .limit(MAX_SUMMARY_FILES)
                .toList();

        final int skipped = Math.max(0, allPaths.size() - filePaths.size());
        final StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Provide a brief, technical summary of this folder's purpose.\n\n");
        promptBuilder.append("=== Folder: ").append(folderPath).append(" ===\n\n");

        for (final Path file : filePaths) {
            try {
                final String content = readFileForSummary(file);
                final String relativePath = pathResolutionService.normalizeRelativePath(folder, file);
                promptBuilder.append("--- File: ").append(relativePath).append(" ---\n");
                promptBuilder.append(content).append("\n\n");
            } catch (final Exception e) {
                // Skip files that can't be read
            }
        }

        return new FolderAnalysisInput(
                promptBuilder.toString(),
                filePaths.size(),
                skipped);
    }
}
