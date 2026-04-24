package ac.uk.sussex.kn253.resources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import ac.uk.sussex.kn253.repository.*;
import ac.uk.sussex.kn253.services.CwdService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@jakarta.ws.rs.Path("/api/workspace")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkspaceResource {

    @Inject
    CwdService cwdService;

    public record WorkspaceProjectListItem(
            long id,
            String directory,
            boolean loaded,
            Object gitRepository,
            Object githubRepository) {
    }

    public record FsEntry(
            String name,
            String path,
            boolean directory,
            String uuid,
            String repoUrl) {
    }

    public record WorkspaceResponse(
            String path,
            String repoUrl,
            List<FsEntry> entries) {
    }

    private List<WorkspaceProjectListItem> listProjects() {
        return ProjectFolder.<ProjectFolder>list("loaded", true).stream()
                .map(project -> new WorkspaceProjectListItem(
                        project.id,
                        project.getDirectory(),
                        project.isLoaded(),
                        project.getGitRepository(),
                        project.getGithubRepository()))
                .toList();
    }

    private WorkspaceResponse workspace(final String path) {
        final ProjectFolder cwdProject = cwdService.getCurrentProject();
        final Path cwdRoot = Path.of(cwdProject.getDirectory()).toAbsolutePath()
                .normalize();
        final Path root = resolveRequestedPath(path, cwdRoot);

        final ProjectFolder projectForPath = ProjectFolder.<ProjectFolder>find("directory", root.toString())
                .firstResult();
        final String repoUrl = projectForPath == null ? resolveRepoUrl(cwdProject) : resolveRepoUrl(projectForPath);

        final List<FsEntry> entries = listEntries(root, repoUrl);
        return new WorkspaceResponse(root.toString(), repoUrl, entries);
    }

    @GET
    @Transactional
    public WorkspaceResponse get(
            @QueryParam("path") final String path) {
        return workspace(path);
    }

    @GET
    @jakarta.ws.rs.Path("/list")
    @Transactional
    public List<WorkspaceProjectListItem> list() {
        return listProjects();
    }

    private Path resolveRequestedPath(final String requestedPath, final Path cwdRoot) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return cwdRoot;
        }

        final Path path = Path.of(requestedPath).toAbsolutePath().normalize();
        if (!path.isAbsolute() || !cwdService.isFolderContained(path)) {
            throw new BadRequestException("Invalid workspace path");
        }
        return path;
    }

    private List<FsEntry> listEntries(final Path root, final String repoUrl) {
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> children = Files.list(root)) {
            return children
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .map(path -> toEntry(path, repoUrl))
                    .toList();
        } catch (final Exception ignored) {
            return List.of();
        }
    }

    private FsEntry toEntry(final Path path, final String projectRepoUrl) {
        final String name = path.getFileName().toString();
        final boolean directory = Files.isDirectory(path);
        final String absolutePath = path.toAbsolutePath().normalize().toString();
        final String uuid = UUID.nameUUIDFromBytes(absolutePath.getBytes(StandardCharsets.UTF_8)).toString();
        final String repoUrl = directory && ".git".equals(name) ? projectRepoUrl : null;
        return new FsEntry(name, absolutePath, directory, uuid, repoUrl);
    }

    private String resolveRepoUrl(final ProjectFolder project) {
        if (project.getGithubRepository() != null && project.getGithubRepository().getRepoUrl() != null
                && !project.getGithubRepository().getRepoUrl().isBlank()) {
            return project.getGithubRepository().getRepoUrl();
        }

        final GitFolder gitRepository = project.getGitRepository();
        if (gitRepository == null || gitRepository.getOrigins() == null) {
            return null;
        }

        for (final Origin origin : gitRepository.getOrigins()) {
            if (origin != null && origin.isGithub() && origin.getUrl() != null) {
                return origin.getUrl().toString();
            }
        }
        return null;
    }
}
