package ac.uk.sussex.kn253.resources;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.services.CwdService;
import ac.uk.sussex.kn253.services.FiletypeKnowledgeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

/**
 * REST endpoints backing lightweight filesystem browser signals.
 */
@Path("/api/fs")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class FsApiResource {

    public record FsBrowserEntryResponse(String name, String path, boolean directory, String repoUrl) {
    }

    public record FsListResponse(String path, List<FsBrowserEntryResponse> entries, String repoUrl) {
    }

    public record FileContentResponse(
            String filePath,
            String content) {
    }

    public record RepoUrlResponse(String repoUrl) {
    }

    public record ProjectSummaryResponse(long id, String directory, boolean hasGitRepository, boolean loaded) {
    }

    public record ProjectStateRequest(long id) {
    }

    @Inject
    CwdService cwdService;

    @Inject
    FiletypeKnowledgeService filetypeKnowledgeService;

    @GET
    @Path("/list")
    public FsListResponse list(@QueryParam("path") final String path) {
        final String resolvedPath = (path == null || path.isBlank()) ? cwdService.getCurrentWorkingDirectory()
                : cwdService.resolveDirectoryPath(path);

        final java.nio.file.Path directory = java.nio.file.Path.of(resolvedPath);
        try (Stream<java.nio.file.Path> children = Files.list(directory)) {
            final List<FsBrowserEntryResponse> entries = children
                    .map(child -> {
                        final boolean isDirectory = Files.isDirectory(child);
                        final String childPath = child.toAbsolutePath().normalize().toString();
                        final String repoUrl = (isDirectory && ".git".equals(child.getFileName().toString()))
                                ? tryResolveRepoUrl(childPath).orElse(null)
                                : null;
                        return new FsBrowserEntryResponse(
                                child.getFileName().toString(),
                                childPath,
                                isDirectory,
                                repoUrl);
                    })
                    .sorted(Comparator
                            .comparing((final FsBrowserEntryResponse e) -> !e.directory())
                            .thenComparing(FsBrowserEntryResponse::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            return new FsListResponse(resolvedPath, entries, tryResolveRepoUrl(resolvedPath).orElse(null));
        } catch (final IOException e) {
            throw new WebApplicationException("Failed to list directory: " + resolvedPath,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("/file")
    public FileContentResponse file(@QueryParam("path") final String path) {
        if (path == null || path.isBlank()) {
            throw new WebApplicationException("Path is required", Response.Status.BAD_REQUEST);
        }

        final java.nio.file.Path target = Paths.get(path.trim()).toAbsolutePath().normalize();
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new WebApplicationException("File not found: " + target, Response.Status.NOT_FOUND);
        }

        try {
            final String content = Files.readString(target, StandardCharsets.UTF_8);
            return new FileContentResponse(
                    target.toString(),
                    content);
        } catch (final IOException e) {
            throw new WebApplicationException("Failed to read file: " + target,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("/file/raw")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response rawFile(@QueryParam("path") final String path) {
        if (path == null || path.isBlank()) {
            throw new WebApplicationException("Path is required", Response.Status.BAD_REQUEST);
        }

        final java.nio.file.Path target = Paths.get(path.trim()).toAbsolutePath().normalize();
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new WebApplicationException("File not found: " + target, Response.Status.NOT_FOUND);
        }

        try {
            final byte[] bytes = Files.readAllBytes(target);
            return Response.ok(bytes)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + target.getFileName() + "\"")
                    .build();
        } catch (final IOException e) {
            throw new WebApplicationException("Failed to read file: " + target,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("/git/open")
    public RepoUrlResponse openGitRemote(@QueryParam("path") final String path) {
        if (path == null || path.isBlank()) {
            throw new WebApplicationException("Path is required", Response.Status.BAD_REQUEST);
        }
        final String resolved = Paths.get(path.trim()).toAbsolutePath().normalize().toString();
        final String repoUrl = tryResolveRepoUrl(resolved)
                .orElseThrow(() -> new WebApplicationException("No GitHub remote found for: " + resolved,
                        Response.Status.NOT_FOUND));
        return new RepoUrlResponse(repoUrl);
    }

    @GET
    @Path("/project")
    public List<ProjectSummaryResponse> listProjects() {
        return ProjectFolder.<ProjectFolder>listAll().stream()
                .map(project -> new ProjectSummaryResponse(
                        project.id == null ? -1L : project.id,
                        project.getDirectory(),
                        project.getGitRepository() != null,
                        project.isLoaded()))
                .toList();
    }

    @POST
    @Path("/project/load")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Map<String, String> markProjectLoaded(final ProjectStateRequest request) {
        final ProjectFolder project = findProjectOrThrow(request);
        project.setLoaded(true);
        return Map.of("status", "loaded");
    }

    @POST
    @Path("/project/unload")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Map<String, String> markProjectUnloaded(final ProjectStateRequest request) {
        final ProjectFolder project = findProjectOrThrow(request);
        project.setLoaded(false);
        return Map.of("status", "unloaded");
    }

    private ProjectFolder findProjectOrThrow(final ProjectStateRequest request) {
        if (request == null || request.id() <= 0) {
            throw new WebApplicationException("Project id is required", Response.Status.BAD_REQUEST);
        }
        final ProjectFolder project = ProjectFolder.findById(request.id());
        if (project == null) {
            throw new WebApplicationException("Project not found: " + request.id(), Response.Status.NOT_FOUND);
        }
        return project;
    }

    private Optional<String> tryResolveRepoUrl(final String path) {
        final java.nio.file.Path rawPath = Paths.get(path).toAbsolutePath().normalize();
        final java.nio.file.Path gitDir = rawPath.getFileName() != null
                && ".git".equals(rawPath.getFileName().toString())
                        ? rawPath
                        : rawPath.resolve(".git");
        final java.nio.file.Path config = gitDir.resolve("config");
        if (!Files.exists(config) || !Files.isRegularFile(config)) {
            return Optional.empty();
        }

        try {
            final List<String> lines = Files.readAllLines(config, StandardCharsets.UTF_8);
            boolean inOrigin = false;
            for (final String line : lines) {
                final String trimmed = line.trim();
                if (trimmed.startsWith("[remote \"")) {
                    inOrigin = "[remote \"origin\"]".equals(trimmed);
                    continue;
                }
                if (!inOrigin) {
                    continue;
                }
                if (trimmed.startsWith("url =")) {
                    final String rawUrl = trimmed.substring("url =".length()).trim();
                    return normalizeGitRemoteToBrowsableUrl(rawUrl);
                }
            }
            return Optional.empty();
        } catch (final IOException e) {
            return Optional.empty();
        }
    }

    private Optional<String> normalizeGitRemoteToBrowsableUrl(final String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        String normalized = rawUrl.trim();

        if (normalized.startsWith("git@github.com:")) {
            normalized = "https://github.com/" + normalized.substring("git@github.com:".length());
        } else if (normalized.startsWith("ssh://git@github.com/")) {
            normalized = "https://github.com/" + normalized.substring("ssh://git@github.com/".length());
        }

        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }

        try {
            final URI uri = URI.create(normalized);
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                return Optional.empty();
            }
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return Optional.empty();
            }
            return Optional.of(normalized);
        } catch (final IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
