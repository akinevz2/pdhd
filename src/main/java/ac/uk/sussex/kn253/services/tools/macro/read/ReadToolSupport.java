package ac.uk.sussex.kn253.services.tools.macro.read;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.model.ProjectKnowledge;
import ac.uk.sussex.kn253.schema.SchemaKeys;
import jakarta.persistence.TransactionRequiredException;

/**
 * Centralized support for caching read tool results to the project knowledge
 * database. Allows tools like read_file, summarize_path, and
 * read_folder_manifest
 * to accumulate context that can be referenced by other tools.
 */
public class ReadToolSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long FILE_CONTENT_TTL_SECONDS = 600L;
    private static final long PATH_ANALYSIS_TTL_SECONDS = 300L;
    private static final long FOLDER_MANIFEST_TTL_SECONDS = 300L;

    public static final String PROJECT_QUERY_FIELD_DIRECTORY = SchemaKeys.DIRECTORY;

    public static final String CACHE_KEY_PREFIX_FILE = "file:";
    public static final String CACHE_KEY_PREFIX_PATH = "path:";
    public static final String CACHE_KEY_PREFIX_FOLDER = "folder:";
    public static final String ANALYSIS_TYPE_DETAILED = "detailed";
    public static final String ANALYSIS_TYPE_SUMMARY = "summary";

    public static final String CACHE_TYPE_FILE_CONTENT = "file_content";
    public static final String CACHE_TYPE_PATH_ANALYSIS = "path_analysis";
    public static final String CACHE_TYPE_FOLDER_MANIFEST = "folder_manifest";

    public static final String ERROR_SERIALIZE_FILE_CONTENT = "Failed to serialize cached file content for ";
    public static final String ERROR_SERIALIZE_PATH_ANALYSIS = "Failed to serialize cached path analysis for ";
    public static final String ERROR_SERIALIZE_FOLDER_MANIFEST = "Failed to serialize cached folder manifest for ";
    public static final String ERROR_PERSISTENCE_UNAVAILABLE_MARKER = "did you forget to annotate your entity with @Entity?";
    public static final String ERROR_TRANSACTION_REQUIRED_MARKER = "transaction is not active";

    public static final String PROJECT_MARKER_POM = "pom.xml";
    public static final String PROJECT_MARKER_GRADLE = "build.gradle";
    public static final String PROJECT_MARKER_PACKAGE = "package.json";
    public static final String PROJECT_MARKER_GIT = ".git";
    public static final String PROJECT_MARKER_SRC = "src";
    public static final String PROJECT_MARKER_README = "README.md";

    /**
     * Resolves or creates a Project entity for a given directory.
     *
     * @param projectDirectory the filesystem path of the project.
     * @return the Project entity, creating one if necessary.
     */
    public Project resolveOrCreateProject(final Path projectDirectory) {
        final String directory = projectDirectory.toString();
        final Project existing = Project.find(PROJECT_QUERY_FIELD_DIRECTORY, directory).firstResult();
        if (existing != null) {
            return existing;
        }

        final Project created = new Project(null, directory, null, null);
        created.persist();
        return created;
    }

    /**
     * Caches the result of a file read operation. The file content is stored
     * keyed by the relative path within the project.
     *
     * @param projectDirectory the project root directory.
     * @param relativePath     the path relative to the project directory.
     * @param content          the file content that was read.
     */
    public void cacheFileContent(
            final Path projectDirectory,
            final String relativePath,
            final String content) {
        try {
            final Instant now = Instant.now();
            final Project project = resolveOrCreateProject(projectDirectory);

            // Use a composite key: "file:relative/path"
            final String cacheKey = CACHE_KEY_PREFIX_FILE + relativePath;

            // Store the file content as a JSON blob with metadata
            final ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put(SchemaKeys.FILE_PATH, relativePath);
            root.put(SchemaKeys.PROJECT_DIRECTORY, projectDirectory.toString());
            root.put(SchemaKeys.CONTENT_LENGTH, content.length());
            root.put(SchemaKeys.CONTENT, content);
            root.put(SchemaKeys.TYPE, CACHE_TYPE_FILE_CONTENT);
            root.put(SchemaKeys.CACHED_AT, now.toString());
            root.put(SchemaKeys.TTL_SECONDS, FILE_CONTENT_TTL_SECONDS);

            final String jsonContent = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root);

            ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project, cacheKey);
            if (knowledge == null) {
                knowledge = new ProjectKnowledge(null, project, cacheKey, jsonContent, now, now);
                knowledge.persist();
            } else {
                knowledge.setJsonContent(jsonContent);
                knowledge.setUpdatedAt(now);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(ERROR_SERIALIZE_FILE_CONTENT + relativePath, e);
        } catch (final RuntimeException e) {
            throw translateCachingRuntimeException("cache file content", e);
        }
    }

    /**
     * Caches a path analysis result (summary or detailed). The analysis is
     * stored keyed by the absolute path and analysis type.
     *
     * @param projectDirectory the project root directory.
     * @param targetPath       the path that was analyzed.
     * @param analysis         the analysis result from PathAnalyzer.
     * @param detailed         whether this was a detailed analysis.
     */
    public void cachePathAnalysis(
            final Path projectDirectory,
            final Path targetPath,
            final String analysis,
            final boolean detailed) {
        try {
            final Instant now = Instant.now();
            final Project project = resolveOrCreateProject(projectDirectory);

            // Use a composite key: "path:detailed:absolute/path" or
            // "path:summary:absolute/path"
            final String cacheKey = CACHE_KEY_PREFIX_PATH + (detailed ? ANALYSIS_TYPE_DETAILED : ANALYSIS_TYPE_SUMMARY)
                    + ":"
                    + targetPath.toAbsolutePath().normalize();

            final ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put(SchemaKeys.TARGET_PATH, targetPath.toAbsolutePath().normalize().toString());
            root.put(SchemaKeys.PROJECT_DIRECTORY, projectDirectory.toString());
            root.put(SchemaKeys.ANALYSIS_TYPE, detailed ? ANALYSIS_TYPE_DETAILED : ANALYSIS_TYPE_SUMMARY);
            root.put(SchemaKeys.ANALYSIS_RESULT, analysis);
            root.put(SchemaKeys.TYPE, CACHE_TYPE_PATH_ANALYSIS);
            root.put(SchemaKeys.CACHED_AT, now.toString());
            root.put(SchemaKeys.TTL_SECONDS, PATH_ANALYSIS_TTL_SECONDS);

            final String jsonContent = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root);

            ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project, cacheKey);
            if (knowledge == null) {
                knowledge = new ProjectKnowledge(null, project, cacheKey, jsonContent, now, now);
                knowledge.persist();
            } else {
                knowledge.setJsonContent(jsonContent);
                knowledge.setUpdatedAt(now);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(ERROR_SERIALIZE_PATH_ANALYSIS + targetPath, e);
        } catch (final RuntimeException e) {
            throw translateCachingRuntimeException("cache path analysis", e);
        }
    }

    /**
     * Caches a folder manifest result. The manifest is stored with the folder
     * path and includes the discovered file structure and sampled contents.
     *
     * @param projectDirectory the project root directory.
     * @param folderPath       the folder that was analyzed.
     * @param manifest         the manifest result from readFolderManifest.
     */
    public void cacheFolderManifest(
            final Path projectDirectory,
            final Path folderPath,
            final String manifest) {
        try {
            final Instant now = Instant.now();
            final Project project = resolveOrCreateProject(projectDirectory);

            // Use a composite key: "folder:absolute/path"
            final String cacheKey = CACHE_KEY_PREFIX_FOLDER + folderPath.toAbsolutePath().normalize();

            final ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put(SchemaKeys.FOLDER_PATH, folderPath.toAbsolutePath().normalize().toString());
            root.put(SchemaKeys.PROJECT_DIRECTORY, projectDirectory.toString());
            root.put(SchemaKeys.MANIFEST_CONTENT, manifest);
            root.put(SchemaKeys.TYPE, CACHE_TYPE_FOLDER_MANIFEST);
            root.put(SchemaKeys.CACHED_AT, now.toString());
            root.put(SchemaKeys.TTL_SECONDS, FOLDER_MANIFEST_TTL_SECONDS);

            final String jsonContent = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root);

            ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project, cacheKey);
            if (knowledge == null) {
                knowledge = new ProjectKnowledge(null, project, cacheKey, jsonContent, now, now);
                knowledge.persist();
            } else {
                knowledge.setJsonContent(jsonContent);
                knowledge.setUpdatedAt(now);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(ERROR_SERIALIZE_FOLDER_MANIFEST + folderPath, e);
        } catch (final RuntimeException e) {
            throw translateCachingRuntimeException("cache folder manifest", e);
        }
    }

    /**
     * Determines the best project directory to use for caching. If an explicit
     * projectDirectory is provided, uses that. Otherwise, attempts to find a
     * project root by walking up from the target path.
     *
     * @param targetPath         the path being read/analyzed.
     * @param explicitProjectDir optional explicit project directory from tool args.
     * @return the project directory to use for caching, or null if unable to
     *         determine.
     */
    public Path resolveProjectDirectory(final Path targetPath, final Path explicitProjectDir) {
        if (explicitProjectDir != null && Files.isDirectory(explicitProjectDir)) {
            return explicitProjectDir;
        }

        // Try to use the target path itself if it's a directory, or its parent
        Path candidate = Files.isDirectory(targetPath) ? targetPath : targetPath.getParent();
        while (candidate != null) {
            // Check if this looks like a project root (has common markers)
            if (hasProjectMarkers(candidate)) {
                return candidate;
            }
            candidate = candidate.getParent();
        }

        // Fallback: use the topmost accessible parent, or the target itself
        return targetPath.toAbsolutePath().normalize();
    }

    /**
     * Checks if a path looks like a project root by checking for common markers.
     *
     * @param path the path to check.
     * @return true if the path appears to be a project root.
     */
    private boolean hasProjectMarkers(final Path path) {
        // Common project root markers
        return Files.exists(path.resolve(PROJECT_MARKER_POM)) ||
                Files.exists(path.resolve(PROJECT_MARKER_GRADLE)) ||
                Files.exists(path.resolve(PROJECT_MARKER_PACKAGE)) ||
                Files.exists(path.resolve(PROJECT_MARKER_GIT)) ||
                Files.exists(path.resolve(PROJECT_MARKER_SRC)) ||
                Files.exists(path.resolve(PROJECT_MARKER_README));
    }

    private RuntimeException translateCachingRuntimeException(final String operation, final RuntimeException e) {
        if (isPersistenceUnavailable(e)) {
            return new UnsupportedOperationException(
                    "Cannot " + operation + " without an active persistence context.",
                    e);
        }
        return e;
    }

    private boolean isPersistenceUnavailable(final RuntimeException e) {
        if (e instanceof TransactionRequiredException || hasCause(e, TransactionRequiredException.class)) {
            return true;
        }

        final String message = e.getMessage();
        if (message != null) {
            final String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains(ERROR_PERSISTENCE_UNAVAILABLE_MARKER.toLowerCase(Locale.ROOT))
                    || lower.contains(ERROR_TRANSACTION_REQUIRED_MARKER)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCause(final Throwable throwable, final Class<? extends Throwable> type) {
        Throwable current = throwable == null ? null : throwable.getCause();
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
