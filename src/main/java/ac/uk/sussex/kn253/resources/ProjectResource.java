package ac.uk.sussex.kn253.resources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import ac.uk.sussex.kn253.repository.*;
import ac.uk.sussex.kn253.services.CwdService;
import ac.uk.sussex.kn253.services.GithubMetadataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@jakarta.ws.rs.Path("/api/project")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    CwdService cwdService;

    @Inject
    GithubMetadataService githubMetadataService;

    public record OpenProjectRequest(String directory) {

    }

    public record ProjectIdRequest(long projectId) {
    }

    public record BrowseProjectRequest(long projectId, String parentUuid) {
    }

    public record ProjectFileRequest(long projectId, String entryUuid) {
    }

    public record ProjectSignalRequest(
            String directory,
            long projectId,
            String parentUuid,
            String entryUuid) {
    }

    public record FileContentResponse(
            String filePath,
            String content,
            String mimeType,
            String language,
            boolean requiresPdfViewer,
            boolean requiresImageViewer,
            boolean requiresMarkdownViewer) {
    }

    public record ProjectRemoteUrlResponse(String remoteUrl) {
    }

    public record FsEntry(
            String name,
            String path,
            boolean directory,
            String uuid,
            String repoUrl) {
    }

    public record BrowseResponse(
            String parentUuid,
            List<FsEntry> entries) {
    }

    private ProjectFolder openImpl(final OpenProjectRequest request) {
        final ProjectFolder project = resolveProject(request);
        project.setLoaded(true);
        githubMetadataService.tryEnrichWithGithubMetadata(project);
        return project;
    }

    private ProjectRemoteUrlResponse remoteImpl(final ProjectIdRequest request) {
        if (request == null || request.projectId() <= 0) {
            throw new BadRequestException("projectId is required");
        }
        final long id = request.projectId();
        final ProjectFolder project = findProjectOrThrow(id);
        return new ProjectRemoteUrlResponse(resolveRemoteUrl(project));
    }

    private BrowseResponse browseImpl(final BrowseProjectRequest request) {
        if (request == null || request.projectId() <= 0) {
            throw new BadRequestException("projectId is required");
        }
        final long id = request.projectId();
        final String parentUuid = request.parentUuid();
        final ProjectFolder project = findProjectOrThrow(id);
        final Path root = Path.of(project.getDirectory()).toAbsolutePath().normalize();
        final Path target = resolvePathByUuid(root, parentUuid);

        if (target == null || !cwdService.isFolderContained(target)) {
            throw new NotFoundException("Folder not found");
        }

        final String repoUrl = resolveRemoteUrl(project);
        return new BrowseResponse(parentUuid == null ? "" : parentUuid, listEntries(target, repoUrl));
    }

    private FileContentResponse fileImpl(final ProjectFileRequest request) {
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
        final Path target = resolvePathByUuid(root, entryUuid);

        if (target == null || !Files.exists(target)) {
            throw new NotFoundException("File not found");
        }

        final Path fileName = target.getFileName();
        if (fileName != null && ".git".equals(fileName.toString())) {

            return new FileContentResponse(
                    target.toString(),
                    loadCommitLogText(project),
                    "text/plain",
                    "text",
                    false,
                    false,
                    true);
        }

        if (Files.isDirectory(target)) {
            throw new NotFoundException("File not found");
        }

        try {
            final String mimeType = detectMimeType(target);
            final boolean requiresPdfViewer = isPdfMimeType(mimeType, target);
            final boolean requiresImageViewer = isImageMimeType(mimeType);
            final boolean requiresMarkdownViewer = isMarkdownPath(target);
            final String content = (requiresPdfViewer || requiresImageViewer)
                    ? null
                    : Files.readString(target, StandardCharsets.UTF_8);

            return new FileContentResponse(
                    target.toString(),
                    content,
                    mimeType,
                    detectLanguage(target, mimeType),
                    requiresPdfViewer,
                    requiresImageViewer,
                    requiresMarkdownViewer);
        } catch (final Exception e) {
            throw new InternalServerErrorException("Failed to read file");
        }
    }

    private Response closeImpl(final ProjectIdRequest request) {
        if (request == null || request.projectId() <= 0) {
            throw new BadRequestException("projectId is required");
        }
        final long id = request.projectId();
        final ProjectFolder project = findProjectOrThrow(id);
        project.setLoaded(false);
        return Response.accepted(project).build();
    }

    @POST
    @jakarta.ws.rs.Path("/open")
    @Transactional
    public ProjectFolder open(final OpenProjectRequest request) {
        return openImpl(request);
    }

    @POST
    @jakarta.ws.rs.Path("/remote")
    @Transactional
    public ProjectRemoteUrlResponse remote(final ProjectIdRequest request) {
        return remoteImpl(request);
    }

    @POST
    @jakarta.ws.rs.Path("/browse")
    @Transactional
    public BrowseResponse browse(final BrowseProjectRequest request) {
        return browseImpl(request);
    }

    @POST
    @jakarta.ws.rs.Path("/file")
    @Transactional
    public FileContentResponse file(final ProjectFileRequest request) {
        return fileImpl(request);
    }

    @DELETE
    @jakarta.ws.rs.Path("/close")
    @Transactional
    public Response close(final ProjectIdRequest request) {
        return closeImpl(request);
    }

    @GET
    @Transactional
    public Response raw(
            @QueryParam("id") final Long id,
            @QueryParam("path") final String path) {
        if (id == null || id <= 0) {
            throw new BadRequestException("id is required");
        }
        return getProjectRawFile(id, path);
    }

    private Response getProjectRawFile(
            final long id,
            final String path) {
        if (path == null || path.isBlank()) {
            throw new BadRequestException("path is required");
        }

        final ProjectFolder project = findProjectOrThrow(id);
        final Path root = Path.of(project.getDirectory()).toAbsolutePath().normalize();
        final Path requested = Path.of(path).toAbsolutePath().normalize();

        if (!requested.startsWith(root) || !cwdService.isFileContained(requested)) {
            throw new BadRequestException("Invalid project file path");
        }

        try {
            final byte[] bytes = Files.readAllBytes(requested);
            final String mimeType = detectMimeType(requested);
            final String mediaType = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
            return Response.ok(bytes, mediaType).build();
        } catch (final Exception e) {
            throw new InternalServerErrorException("Failed to read raw file");
        }
    }

    private ProjectFolder resolveProject(final OpenProjectRequest request) {
        if (request == null || request.directory() == null || request.directory().isBlank()) {
            return cwdService.getCurrentProject();
        }

        final String resolvedDirectory = Path.of(request.directory()).toAbsolutePath().normalize()
                .toString();
        final ProjectFolder existing = ProjectFolder.<ProjectFolder>find("directory", resolvedDirectory).firstResult();
        if (existing != null) {
            return existing;
        }

        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(resolvedDirectory);
        project.setLoaded(false);
        project.persist();
        return project;
    }

    private ProjectFolder findProjectOrThrow(final long id) {
        final ProjectFolder project = ProjectFolder.<ProjectFolder>findById(id);
        if (project == null) {
            throw new NotFoundException("Project not found");
        }
        return project;
    }

    private List<FsEntry> listEntries(final Path directory, final String repoUrl) {
        try (Stream<Path> children = Files.list(directory)) {
            return children
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .map(path -> new FsEntry(
                            path.getFileName().toString(),
                            path.toAbsolutePath().normalize().toString(),
                            Files.isDirectory(path),
                            uuidForPath(path),
                            ".git".equals(path.getFileName().toString()) ? repoUrl : null))
                    .toList();
        } catch (final Exception e) {
            return List.of();
        }
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

    private boolean isMarkdownPath(final Path path) {
        final String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".mdx");
    }

    private String detectLanguage(final Path path, final String mimeType) {
        final String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".ts") || name.endsWith(".tsx")) {
            return "typescript";
        }
        if (name.endsWith(".js") || name.endsWith(".jsx")) {
            return "javascript";
        }
        if (name.endsWith(".java")) {
            return "java";
        }
        if (isMarkdownPath(path)) {
            return "markdown";
        }
        if (mimeType != null && mimeType.toLowerCase().contains("json")) {
            return "json";
        }
        return "text";
    }

    private String resolveRemoteUrl(final ProjectFolder project) {
        if (project.getGithubRepository() != null && project.getGithubRepository().getRepoUrl() != null
                && !project.getGithubRepository().getRepoUrl().isBlank()) {
            return project.getGithubRepository().getRepoUrl();
        }
        final GitFolder gitRepository = project.getGitRepository();
        if (gitRepository != null && gitRepository.getOrigins() != null) {
            for (final Origin origin : gitRepository.getOrigins()) {
                if (origin != null && origin.isGithub() && origin.getUrl() != null) {
                    return origin.getUrl().toString();
                }
            }
        }
        final String remote = runGitCommand(project, "git", "config", "--get", "remote.origin.url");
        if (remote == null || remote.isBlank()) {
            return null;
        }
        return remote.contains("github.com") ? remote : null;
    }

    private String loadCommitLogText(final ProjectFolder project) {
        final String output = runGitCommand(project, "git", "log", "--date=short",
                "--pretty=format:%h %ad %an %s", "-n", "30");
        return output == null ? "" : output;
    }

    private String runGitCommand(final ProjectFolder project, final String... command) {
        final String directory = project.getDirectory();
        if (directory == null || directory.isBlank()) {
            return null;
        }
        if (!Files.exists(Path.of(directory))) {
            return null;
        }
        try {
            final Process process = new ProcessBuilder(command)
                    .directory(new java.io.File(directory))
                    .redirectErrorStream(true)
                    .start();
            final byte[] bytes = process.getInputStream().readAllBytes();
            final boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                if (!finished) {
                    process.destroyForcibly();
                }
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (final Exception e) {
            return null;
        }
    }

}
