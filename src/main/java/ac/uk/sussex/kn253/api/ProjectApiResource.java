package ac.uk.sussex.kn253.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import ac.uk.sussex.kn253.api.model.*;
import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.services.ToolActivityService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@jakarta.ws.rs.Path("/api")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ProjectApiResource {

    private static final int MAX_TREE_DEPTH = 5;
    private static final int MAX_FILE_BYTES = 1024 * 1024;

    @Inject
    ToolActivityService toolActivityService;

    @GET
    @jakarta.ws.rs.Path("/projects")
    public List<ProjectSummaryResponse> projects() {
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
}
