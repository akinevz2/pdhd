package ac.uk.sussex.kn253.resources;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import ac.uk.sussex.kn253.entities.fs.Project;
import ac.uk.sussex.kn253.entities.fs.ProjectKnowledge;
import ac.uk.sussex.kn253.resources.api.*;
import ac.uk.sussex.kn253.resources.api.model.*;
import ac.uk.sussex.kn253.schema.*;
import ac.uk.sussex.kn253.services.ToolTelemetryService;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource exposing project, filesystem, and telemetry APIs under
 * {@code /api}.
 *
 * <p>
 * Endpoints:
 * <ul>
 * <li>{@code GET  /api/projects} – list projects (live scan + DB tags).</li>
 * <li>{@code GET  /api/cwd} – return the current working directory.</li>
 * <li>{@code POST /api/cwd} – change the current working directory.</li>
 * <li>{@code GET  /api/fs/dirs} – suggest matching sub-directories.</li>
 * <li>{@code GET  /api/fs/list} – immediate directory listing.</li>
 * <li>{@code GET  /api/fs/tree?path=} – recursive file-tree for a
 * directory.</li>
 * <li>{@code GET  /api/fs/file?path=} – read a text file by absolute path.</li>
 * <li>{@code GET  /api/fs/file/raw?path=} – serve a raw image by absolute
 * path.</li>
 * <li>{@code GET  /api/projects/{id}/knowledge} – list knowledge entries.</li>
 * <li>{@code PUT  /api/projects/{id}/knowledge/{key}} – upsert a knowledge
 * entry.</li>
 * <li>{@code DELETE /api/projects/{id}/knowledge/{key}} – delete a knowledge
 * entry.</li>
 * <li>{@code GET  /api/tool-telemetry} – per-tool execution telemetry
 * metrics.</li>
 * </ul>
 *
 * <p>
 * Projects are filesystem tags stored in the database so the agent can attach
 * persistent knowledge to them. File operations use absolute paths directly.
 */
@jakarta.ws.rs.Path("/api")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ProjectApiResource {

    private static final int MAX_TREE_DEPTH = 5;
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_IMAGE_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIR_SUGGESTIONS = 24;
    private static final int MAX_LIST_ENTRIES = 500;
    private static final String STRING_LITERAL_NULL = "null";
    private static final String HEADER_CACHE_CONTROL = "Cache-Control";
    private static final String CACHE_CONTROL_NO_STORE = "no-store";
    private static final String ERROR_MISSING_REQUIRED_FIELD_PREFIX = "Missing required field: ";
    private static final String ERROR_KNOWLEDGE_KEY_BLANK = "Knowledge key must not be blank";
    private static final String ERROR_KNOWLEDGE_NOT_FOUND = "Knowledge entry not found";
    private static final String ERROR_PROJECT_NOT_FOUND = "Project not found";
    private static final String ERROR_FILE_NOT_FOUND = "File not found";
    private static final String ERROR_FILE_TOO_LARGE_INLINE_PREVIEW = "File too large for inline preview";
    private static final String ERROR_IMAGE_TOO_LARGE_INLINE_PREVIEW = "Image too large for inline preview";
    private static final String ERROR_ONLY_IMAGE_FILES_SUPPORTED = "Only image files are supported";
    private static final String ERROR_FAILED_TO_READ_FILE = "Failed to read file";
    private static final String SUMMARY_TOOL_TELEMETRY_PREFIX = "Telemetry available for ";
    private static final String SUMMARY_TOOL_TELEMETRY_SUFFIX = " tool(s).";
    private static final char WINDOWS_PATH_SEPARATOR = '\\';
    private static final char UNIX_PATH_SEPARATOR = '/';

    @Inject
    ToolTelemetryService toolTelemetryService;

    @Inject
    WorkingDirectoryService workingDirectoryService;

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    /**
     * Returns projects discovered under the current working directory.
     *
     * <p>
     * Performs a live filesystem walk and ensures each discovered directory has
     * a corresponding {@link Project} tag in the database.
     *
     * @return ordered list of project summaries.
     * @throws IOException if there are filesystem errors during discovery
     *                     are propagated to the caller.
     */
    @GET
    @Path("/projects")
    @Transactional
    public List<@NonNull ProjectSummaryResponse> projects() throws IOException {
        // projectDiscoveryService.discover();
        // return Project.<Project>streamAll()
        // .map(ProjectSummaryResponse::new)
        // .toList();
        throw new UnsupportedOperationException("Project discovery not implemented yet");
    }

    /**
     * Returns all knowledge entries for a project.
     *
     * @param id project database ID.
     */
    @GET
    @Path("/projects/{id}/knowledge")
    public List<Map<String, Object>> listKnowledge(@PathParam("id") final long id) {
        final Project project = requireProject(id);
        return ProjectKnowledge.<ProjectKnowledge>list(SchemaKeys.PROJECT, project).stream()
                .sorted(Comparator.comparing(ProjectKnowledge::getKey))
                .map(k -> {
                    final Map<String, Object> m = new LinkedHashMap<>();
                    m.put(SchemaKeys.ID, k.id);
                    m.put(SchemaKeys.KEY, k.getKey());
                    m.put(SchemaKeys.JSON_CONTENT, k.getJsonContent());
                    m.put(SchemaKeys.UPDATED_AT, k.getUpdatedAt().toString());
                    return m;
                })
                .toList();
    }

    /**
     * Upserts a knowledge entry for a project.
     *
     * @param id   project database ID.
     * @param key  knowledge key.
     * @param body JSON body with a {@code "jsonContent"} field.
     */
    @PUT
    @Path("/projects/{id}/knowledge/{key}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Map<String, Object> putKnowledge(
            @PathParam("id") final long id,
            @PathParam("key") final String key,
            final Map<String, Object> body) {
        if (key == null || key.isBlank()) {
            throw new WebApplicationException(ERROR_KNOWLEDGE_KEY_BLANK, Response.Status.BAD_REQUEST);
        }
        final String jsonContent = body == null ? null : String.valueOf(body.get(SchemaKeys.JSON_CONTENT));
        if (jsonContent == null || jsonContent.equals(STRING_LITERAL_NULL)) {
            throw new WebApplicationException(ERROR_MISSING_REQUIRED_FIELD_PREFIX + SchemaKeys.JSON_CONTENT,
                    Response.Status.BAD_REQUEST);
        }
        final Project project = requireProject(id);
        final Instant now = Instant.now();
        ProjectKnowledge entry = ProjectKnowledge.findByProjectAndKey(project, key);
        if (entry == null) {
            entry = new ProjectKnowledge(null, project, key, jsonContent, now, now, null);
        } else {
            entry.setJsonContent(jsonContent);
            entry.setUpdatedAt(now);
        }
        entry.persist();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put(SchemaKeys.ID, entry.id);
        result.put(SchemaKeys.KEY, entry.getKey());
        result.put(SchemaKeys.JSON_CONTENT, entry.getJsonContent());
        result.put(SchemaKeys.UPDATED_AT, entry.getUpdatedAt().toString());
        return result;
    }

    /**
     * Deletes a knowledge entry.
     *
     * @param id  project database ID.
     * @param key knowledge key.
     */
    @DELETE
    @Path("/projects/{id}/knowledge/{key}")
    @Transactional
    public Response deleteKnowledge(
            @PathParam("id") final long id,
            @PathParam("key") final String key) {
        final Project project = requireProject(id);
        final ProjectKnowledge entry = ProjectKnowledge.findByProjectAndKey(project, key);
        if (entry == null) {
            throw new WebApplicationException(ERROR_KNOWLEDGE_NOT_FOUND, Response.Status.NOT_FOUND);
        }
        entry.delete();
        return Response.noContent().build();
    }

    /** Returns the current working directory. */
    @GET
    @Path("/cwd")
    public Map<String, String> cwd() {
        return Map.of(SchemaKeys.CWD, workingDirectoryService.getCurrentWorkingDirectory().toString());
    }

    /**
     * Changes the current working directory.
     *
     * @param request JSON body containing {@code "path"}.
     * @return the updated CWD.
     */
    @POST
    @Path("/cwd")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, String> setCwd(final Map<String, String> request) {
        // final String path = request == null ? null : request.get(SchemaKeys.PATH);
        // if (path == null || path.isBlank()) {
        // throw new WebApplicationException(ERROR_MISSING_REQUIRED_FIELD_PREFIX +
        // SchemaKeys.PATH,
        // Response.Status.BAD_REQUEST);
        // }
        // try {
        // return Map.of(SchemaKeys.CWD,
        // workingDirectoryService.navigateTo(path).toString());
        // } catch (final IllegalArgumentException e) {
        // throw new WebApplicationException(e.getMessage(),
        // Response.Status.BAD_REQUEST);
        // }
        throw new UnsupportedOperationException("Changing working directory not implemented yet");
    }

    /**
     * Returns sub-directory suggestions for filesystem path navigation.
     *
     * @param pathQuery partial or full path to filter; defaults to CWD.
     * @return map with key {@code "dirs"} containing matching directory paths.
     */
    @GET
    @Path("/fs/dirs")
    public Map<String, List<String>> fsDirs(
            @QueryParam("path") @DefaultValue("") final String pathQuery) {
        final String resolved = pathQuery.isBlank()
                ? workingDirectoryService.getCurrentWorkingDirectory().toString()
                : pathQuery;

        final java.nio.file.Path query = java.nio.file.Path.of(resolved).toAbsolutePath().normalize();
        final java.nio.file.Path listDir;
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
            return Map.of(SchemaKeys.DIRS, List.of());
        }

        try (Stream<java.nio.file.Path> stream = Files.list(listDir)) {
            final List<String> dirs = stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(Comparator.comparing(
                            p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(MAX_DIR_SUGGESTIONS)
                    .map(p -> p.toAbsolutePath().normalize().toString())
                    .toList();
            return Map.of(SchemaKeys.DIRS, dirs);
        } catch (final IOException e) {
            return Map.of(SchemaKeys.DIRS, List.of());
        }
    }

    /**
     * Returns immediate filesystem entries for a directory.
     *
     * @param pathQuery absolute or relative directory path; defaults to CWD.
     * @return map with keys {@code path}, {@code entries}, and optional
     *         {@code repoUrl}. Each entry also includes an optional
     *         {@code repoUrl} when that child directory is a git repository with a
     *         browsable remote.
     */
    @GET
    @Path("/fs/list")
    public Map<String, Object> fsList(
            @QueryParam("path") @DefaultValue("") final String pathQuery) {
        // final java.nio.file.Path directory = pathQuery.isBlank()
        // ? workingDirectoryService.getCurrentWorkingDirectory()
        // : workingDirectoryService.resolveAgainstCurrent(pathQuery);

        // if (!Files.exists(directory) || !Files.isDirectory(directory)) {
        // throw new WebApplicationException(BackendSupport.ERROR_NOT_A_DIRECTORY,
        // Response.Status.BAD_REQUEST);
        // }

        // try (Stream<java.nio.file.Path> stream = Files.list(directory)) {
        // final List<Map<String, Object>> entries = stream
        // .filter(p -> p.getFileName() != null)
        // .sorted((a, b) -> {
        // final boolean aDir = Files.isDirectory(a);
        // final boolean bDir = Files.isDirectory(b);
        // if (aDir != bDir) {
        // return aDir ? -1 : 1;
        // }
        // return
        // a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
        // })
        // .limit(MAX_LIST_ENTRIES)
        // .map(this::toFsEntry)
        // .toList();
        // final Map<String, Object> response = new LinkedHashMap<>();
        // response.put(SchemaKeys.PATH,
        // directory.toAbsolutePath().normalize().toString());
        // response.put(SchemaKeys.ENTRIES, entries);
        // try {
        // response.put(SchemaKeys.REPO_URL, getRepositoryUrl(directory));
        // } catch (final NotAGitRepositoryException | NotAGithubRepositoryException e)
        // {
        // response.put(SchemaKeys.REPO_URL, ToolSupport.VALUE_REPO_URL_ABSENT);
        // }
        // return response;
        // } catch (final IOException e) {
        // throw new
        // WebApplicationException(BackendSupport.ERROR_FAILED_TO_LIST_DIRECTORY,
        // Response.Status.INTERNAL_SERVER_ERROR);
        // }
        throw new UnsupportedOperationException("Filesystem listing not implemented yet");
    }

    private Map<String, Object> toFsEntry(final java.nio.file.Path path) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        final boolean isDirectory = Files.isDirectory(path);
        entry.put(SchemaKeys.NAME, path.getFileName().toString());
        entry.put(SchemaKeys.PATH, path.toAbsolutePath().normalize().toString());
        entry.put(SchemaKeys.DIRECTORY, isDirectory);

        // Only annotate repository links for project/repository root folders.
        final boolean isRepoRoot = isDirectory && Files.isDirectory(path.resolve(BackendSupport.DIR_GIT));
        if (!isRepoRoot) {
            entry.put(SchemaKeys.REPO_URL, ToolSupport.VALUE_REPO_URL_ABSENT);
            return entry;
        }

        try {
            entry.put(SchemaKeys.REPO_URL, getRepositoryUrl(path));
        } catch (final NotAGitRepositoryException | NotAGithubRepositoryException e) {
            entry.put(SchemaKeys.REPO_URL, ToolSupport.VALUE_REPO_URL_ABSENT);
        }
        return entry;
    }

    private URL getRepositoryUrl(final java.nio.file.Path path)
            throws NotAGitRepositoryException, NotAGithubRepositoryException {
        // return repoService.getBrowsableGithubRepositoryUrl(path);
        throw new UnsupportedOperationException("Git repository URL detection not implemented yet");
    }

    /**
     * Returns a recursive file-tree for a directory.
     *
     * @param absolutePath absolute path to the directory root.
     */
    @GET
    @Path("/fs/tree")
    public ProjectTreeNodeResponse fsTree(
            @QueryParam("path") final String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            throw new WebApplicationException(BackendSupport.ERROR_MISSING_QUERY_PATH, Response.Status.BAD_REQUEST);
        }
        final java.nio.file.Path root = java.nio.file.Path.of(absolutePath).toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new WebApplicationException(BackendSupport.ERROR_NOT_A_DIRECTORY, Response.Status.NOT_FOUND);
        }
        return toNode(root, root, 0);
    }

    /**
     * Returns the UTF-8 text content of a file.
     *
     * @param absolutePath absolute path to the file.
     */
    @GET
    @Path("/fs/file")
    public FileContentResponse fsFile(
            @QueryParam("path") final String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            throw new WebApplicationException(BackendSupport.ERROR_MISSING_QUERY_PATH, Response.Status.BAD_REQUEST);
        }
        final java.nio.file.Path file = java.nio.file.Path.of(absolutePath).toAbsolutePath().normalize();
        requireFile(file);
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new WebApplicationException(ERROR_FILE_TOO_LARGE_INLINE_PREVIEW,
                        Response.Status.BAD_REQUEST);
            }
            return new FileContentResponse(file.toString(),
                    Files.readString(file, StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new WebApplicationException(ERROR_FAILED_TO_READ_FILE,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Serves a raw image file for inline preview.
     *
     * @param absolutePath absolute path to the image file.
     */
    @GET
    @Path("/fs/file/raw")
    @Produces(MediaType.WILDCARD)
    public Response fsFileRaw(
            @QueryParam("path") final String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            throw new WebApplicationException(BackendSupport.ERROR_MISSING_QUERY_PATH, Response.Status.BAD_REQUEST);
        }
        final java.nio.file.Path file = java.nio.file.Path.of(absolutePath).toAbsolutePath().normalize();
        requireFile(file);
        try {
            if (Files.size(file) > MAX_IMAGE_FILE_BYTES) {
                throw new WebApplicationException(ERROR_IMAGE_TOO_LARGE_INLINE_PREVIEW,
                        Response.Status.BAD_REQUEST);
            }
            final String mediaType = ImageMediaType.detect(file);
            if (mediaType == null) {
                throw new WebApplicationException(ERROR_ONLY_IMAGE_FILES_SUPPORTED,
                        Response.Status.BAD_REQUEST);
            }
            return Response.ok(Files.readAllBytes(file), mediaType)
                    .header(HEADER_CACHE_CONTROL, CACHE_CONTROL_NO_STORE)
                    .build();
        } catch (final IOException e) {
            throw new WebApplicationException(ERROR_FAILED_TO_READ_FILE,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Returns per-tool execution telemetry metrics as a versioned API payload.
     */
    @GET
    @Path("/tool-telemetry")
    public ToolTelemetryResponse toolTelemetry() {
        // final List<ToolTelemetryResponse.@NonNull ToolTelemetryItem> items =
        // toolTelemetryService.snapshot().stream()
        // .map(ToolTelemetryResponse.ToolTelemetryItem::new)
        // .toList();

        // final String summary = SUMMARY_TOOL_TELEMETRY_PREFIX + items.size() +
        // SUMMARY_TOOL_TELEMETRY_SUFFIX;
        // return new ToolTelemetryResponse(
        // BackendSupport.SCHEMA_TOOL_TELEMETRY_V1,
        // Instant.now().toString(),
        // summary,
        // items);
        throw new UnsupportedOperationException("Tool telemetry API not implemented yet");
    }

    // -------------------------------------------------------------------------
    // Tree builder
    // -------------------------------------------------------------------------

    private ProjectTreeNodeResponse toNode(
            final java.nio.file.Path projectRoot,
            final java.nio.file.Path path,
            final int depth) {
        final String name = path.getFileName() != null
                ? path.getFileName().toString()
                : path.toString();
        final String relative = projectRoot.equals(path)
                ? ""
                : projectRoot.relativize(path).toString().replace(WINDOWS_PATH_SEPARATOR, UNIX_PATH_SEPARATOR);
        final boolean isDirectory = Files.isDirectory(path);

        if (!isDirectory || depth >= MAX_TREE_DEPTH) {
            return new ProjectTreeNodeResponse(name, relative, isDirectory, List.of());
        }

        try (Stream<java.nio.file.Path> stream = Files.list(path)) {
            final List<ProjectTreeNodeResponse> children = stream
                    .sorted(Comparator.comparing(
                            p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(child -> toNode(projectRoot, child, depth + 1))
                    .toList();
            return new ProjectTreeNodeResponse(name, relative, true, children);
        } catch (final IOException e) {
            return new ProjectTreeNodeResponse(name, relative, true, List.of());
        }
    }

    // -------------------------------------------------------------------------
    // Guard helpers
    // -------------------------------------------------------------------------

    private Project requireProject(final long id) {
        final Project project = Project.findById(id);
        if (project == null) {
            throw new WebApplicationException(ERROR_PROJECT_NOT_FOUND, Response.Status.NOT_FOUND);
        }
        return project;
    }

    private void requireFile(final java.nio.file.Path file) {
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new WebApplicationException(ERROR_FILE_NOT_FOUND, Response.Status.NOT_FOUND);
        }
    }
}
