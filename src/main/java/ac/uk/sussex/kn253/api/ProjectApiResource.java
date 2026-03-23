package ac.uk.sussex.kn253.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import ac.uk.sussex.kn253.api.model.*;
import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.services.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@jakarta.ws.rs.Path("/api")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ProjectApiResource {

    private static final int MAX_TREE_DEPTH = 5;
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_IMAGE_FILE_BYTES = 5 * 1024 * 1024;
    private static final int PROJECT_SCAN_DEPTH = 4;

    @Inject
    ToolActivityService toolActivityService;

    @Inject
    RepoService repoService;

    @Inject
    WorkingDirectoryService workingDirectoryService;

    @GET
    @jakarta.ws.rs.Path("/projects")
    @Transactional
    public List<ProjectSummaryResponse> projects() {
        discoverProjectsFromCurrentWorkingDirectory();
        return Project.<Project>listAll().stream()
                .sorted(Comparator.comparing(p -> p.id))
                .map(project -> new ProjectSummaryResponse(
                        project.id,
                        project.getDirectory(),
                        project.getGitRepository() != null,
                        project.getGithubRepository() != null ? project.getGithubRepository().getName() : null,
                        project.getGithubRepository() != null ? project.getGithubRepository().getDescription() : null))
                .toList();
    }

    private void discoverProjectsFromCurrentWorkingDirectory() {
        final Path currentDirectory = workingDirectoryService.getCurrentWorkingDirectory();
        ensureProjectExists(currentDirectory);

        try (Stream<Path> stream = Files.walk(currentDirectory, PROJECT_SCAN_DEPTH)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> path.getFileName().toString().equals(".git"))
                    .map(Path::getParent)
                    .filter(Objects::nonNull)
                    .forEach(this::ensureProjectExists);
        } catch (final IOException ignored) {
            // Keep API resilient; at least current directory is ensured above.
        }
    }

    private void ensureProjectExists(final Path directoryPath) {
        final String directory = directoryPath.toAbsolutePath().normalize().toString();
        final Project existing = Project.find("directory", directory).firstResult();
        if (existing == null) {
            repoService.resolveProject(Path.of(directory));
        }
    }

    @GET
    @jakarta.ws.rs.Path("/cwd")
    public Map<String, String> cwd() {
        return Map.of("cwd", workingDirectoryService.getCurrentWorkingDirectory().toString());
    }

    @POST
    @jakarta.ws.rs.Path("/cwd")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, String> setCwd(final Map<String, String> request) {
        final String path = request == null ? null : request.get("path");
        if (path == null || path.isBlank()) {
            throw new WebApplicationException("Missing required field: path", Response.Status.BAD_REQUEST);
        }
        try {
            final Path updated = workingDirectoryService.navigateTo(path);
            return Map.of("cwd", updated.toString());
        } catch (final IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        }
    }

    @GET
    @jakarta.ws.rs.Path("/fs/dirs")
    public Map<String, List<String>> fsDirs(@QueryParam("path") @DefaultValue("") final String pathQuery) {
        final String resolved = pathQuery.isBlank()
                ? workingDirectoryService.getCurrentWorkingDirectory().toString()
                : pathQuery;

        final Path query = Path.of(resolved).toAbsolutePath().normalize();
        final Path listDir;
        final String prefix;

        if (Files.isDirectory(query)) {
            listDir = query;
            prefix = "";
        } else {
            listDir = query.getParent();
            prefix = query.getFileName() != null
                    ? query.getFileName().toString().toLowerCase(Locale.ROOT)
                    : "";
        }

        if (listDir == null || !Files.isDirectory(listDir)) {
            return Map.of("dirs", List.of());
        }

        try (Stream<Path> stream = Files.list(listDir)) {
            final List<String> dirs = stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(24)
                    .map(p -> p.toAbsolutePath().normalize().toString())
                    .toList();
            return Map.of("dirs", dirs);
        } catch (final IOException e) {
            return Map.of("dirs", List.of());
        }
    }

    @GET
    @jakarta.ws.rs.Path("/projects/{id}/tree")
    public ProjectTreeNodeResponse tree(
            @PathParam("id") final long id,
            @QueryParam("path") @DefaultValue("") final String relativePath) {
        final Project project = Project.findById(id);
        if (project == null) {
            throw new WebApplicationException("Project not found", Response.Status.NOT_FOUND);
        }

        final Path root = Path.of(project.getDirectory()).toAbsolutePath().normalize();
        final Path target = relativePath.isBlank()
                ? root
                : root.resolve(relativePath).toAbsolutePath().normalize();
        ensureUnderProject(root, target);

        if (!Files.exists(target)) {
            throw new WebApplicationException("Path not found", Response.Status.NOT_FOUND);
        }
        return toNode(root, target, 0);
    }

    @GET
    @jakarta.ws.rs.Path("/projects/{id}/file")
    public FileContentResponse file(
            @PathParam("id") final long id,
            @QueryParam("path") final String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new WebApplicationException("Missing query parameter: path", Response.Status.BAD_REQUEST);
        }

        final Project project = Project.findById(id);
        if (project == null) {
            throw new WebApplicationException("Project not found", Response.Status.NOT_FOUND);
        }

        final Path root = Path.of(project.getDirectory()).toAbsolutePath().normalize();
        final Path file = root.resolve(relativePath).toAbsolutePath().normalize();
        ensureUnderProject(root, file);

        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new WebApplicationException("File not found", Response.Status.NOT_FOUND);
        }

        try {
            final long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                throw new WebApplicationException("File too large for inline preview", Response.Status.BAD_REQUEST);
            }
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            return new FileContentResponse(root.toString(), relativePath, content);
        } catch (final IOException e) {
            throw new WebApplicationException("Failed to read file", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @jakarta.ws.rs.Path("/projects/{id}/file/raw")
    @Produces(MediaType.WILDCARD)
    public Response rawFile(
            @PathParam("id") final long id,
            @QueryParam("path") final String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new WebApplicationException("Missing query parameter: path", Response.Status.BAD_REQUEST);
        }

        final Project project = Project.findById(id);
        if (project == null) {
            throw new WebApplicationException("Project not found", Response.Status.NOT_FOUND);
        }

        final Path root = Path.of(project.getDirectory()).toAbsolutePath().normalize();
        final Path file = root.resolve(relativePath).toAbsolutePath().normalize();
        ensureUnderProject(root, file);

        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new WebApplicationException("File not found", Response.Status.NOT_FOUND);
        }

        try {
            final long size = Files.size(file);
            if (size > MAX_IMAGE_FILE_BYTES) {
                throw new WebApplicationException("Image too large for inline preview", Response.Status.BAD_REQUEST);
            }

            final String mediaType = detectImageMediaType(file);
            if (mediaType == null) {
                throw new WebApplicationException("Only image files are supported", Response.Status.BAD_REQUEST);
            }

            final byte[] bytes = Files.readAllBytes(file);
            return Response.ok(bytes, mediaType)
                    .header("Cache-Control", "no-store")
                    .build();
        } catch (final IOException e) {
            throw new WebApplicationException("Failed to read file", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @jakarta.ws.rs.Path("/tool-activity")
    public ToolActivityResponse toolActivity(@QueryParam("limit") @DefaultValue("100") final int limit) {
        final List<ToolActivityResponse.ToolActivityItem> items = toolActivityService.recent(limit).stream()
                .map(event -> new ToolActivityResponse.ToolActivityItem(
                        event.timestamp(),
                        event.toolName(),
                        event.argumentsJson(),
                        event.result(),
                        event.requestedFiles()))
                .toList();
        return new ToolActivityResponse(items);
    }

    private ProjectTreeNodeResponse toNode(final Path projectRoot, final Path path, final int depth) {
        final String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        final String relative = projectRoot.equals(path) ? ""
                : projectRoot.relativize(path).toString().replace('\\', '/');
        final boolean isDirectory = Files.isDirectory(path);

        if (!isDirectory || depth >= MAX_TREE_DEPTH) {
            return new ProjectTreeNodeResponse(name, relative, isDirectory, List.of());
        }

        try {
            final List<ProjectTreeNodeResponse> children = Files.list(path)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .map(child -> toNode(projectRoot, child, depth + 1))
                    .toList();
            return new ProjectTreeNodeResponse(name, relative, true, children);
        } catch (final IOException e) {
            return new ProjectTreeNodeResponse(name, relative, true, List.of());
        }
    }

    private void ensureUnderProject(final Path projectRoot, final Path target) {
        if (!target.startsWith(projectRoot)) {
            throw new WebApplicationException("Invalid path", Response.Status.BAD_REQUEST);
        }
    }

    private String detectImageMediaType(final Path file) {
        try {
            final String detected = Files.probeContentType(file);
            if (detected != null && detected.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return detected;
            }
        } catch (final IOException ignored) {
            // Fall back to extension checks.
        }

        final String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return null;
    }
}
