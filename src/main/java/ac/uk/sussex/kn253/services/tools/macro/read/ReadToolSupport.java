package ac.uk.sussex.kn253.services.tools.macro.read;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.model.ProjectKnowledge;

/**
 * Centralized support for caching read tool results to the project knowledge
 * database. Allows tools like read_file, summarize_path, and
 * read_folder_manifest
 * to accumulate context that can be referenced by other tools.
 */
public class ReadToolSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Resolves or creates a Project entity for a given directory.
     *
     * @param projectDirectory the filesystem path of the project.
     * @return the Project entity, creating one if necessary.
     */
    public Project resolveOrCreateProject(final Path projectDirectory) {
        final String directory = projectDirectory.toString();
        final Project existing = Project.find("directory", directory).firstResult();
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
     * @return a success or error message.
     */
    public String cacheFileContent(
            final Path projectDirectory,
            final String relativePath,
            final String content) {
        try {
            final Instant now = Instant.now();
            final Project project = resolveOrCreateProject(projectDirectory);

            // Use a composite key: "file:relative/path"
            final String cacheKey = "file:" + relativePath;

            // Store the file content as a JSON blob with metadata
            final ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("filePath", relativePath);
            root.put("projectDirectory", projectDirectory.toString());
            root.put("contentLength", content.length());
            root.put("content", content);
            root.put("type", "file_content");

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

            return "Cached file content: " + projectDirectory + "/" + relativePath;
        } catch (final IOException e) {
            // Fail silently - don't break tool execution if caching fails
            return null;
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
     * @return a success or error message, or null if caching failed silently.
     */
    public String cachePathAnalysis(
            final Path projectDirectory,
            final Path targetPath,
            final String analysis,
            final boolean detailed) {
        try {
            final Instant now = Instant.now();
            final Project project = resolveOrCreateProject(projectDirectory);

            // Use a composite key: "path:detailed:absolute/path" or
            // "path:summary:absolute/path"
            final String cacheKey = "path:" + (detailed ? "detailed" : "summary") + ":"
                    + targetPath.toAbsolutePath().normalize();

            final ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("targetPath", targetPath.toAbsolutePath().normalize().toString());
            root.put("projectDirectory", projectDirectory.toString());
            root.put("analysisType", detailed ? "detailed" : "summary");
            root.put("analysisResult", analysis);
            root.put("type", "path_analysis");

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

            return "Cached path analysis: " + targetPath;
        } catch (final IOException e) {
            // Fail silently - don't break tool execution if caching fails
            return null;
        }
    }

    /**
     * Caches a folder manifest result. The manifest is stored with the folder
     * path and includes the discovered file structure and sampled contents.
     *
     * @param projectDirectory the project root directory.
     * @param folderPath       the folder that was analyzed.
     * @param manifest         the manifest result from readFolderManifest.
     * @return a success or error message, or null if caching failed silently.
     */
    public String cacheFolderManifest(
            final Path projectDirectory,
            final Path folderPath,
            final String manifest) {
        try {
            final Instant now = Instant.now();
            final Project project = resolveOrCreateProject(projectDirectory);

            // Use a composite key: "folder:absolute/path"
            final String cacheKey = "folder:" + folderPath.toAbsolutePath().normalize();

            final ObjectNode root = OBJECT_MAPPER.createObjectNode();
            root.put("folderPath", folderPath.toAbsolutePath().normalize().toString());
            root.put("projectDirectory", projectDirectory.toString());
            root.put("manifestContent", manifest);
            root.put("type", "folder_manifest");

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

            return "Cached folder manifest: " + folderPath;
        } catch (final IOException e) {
            // Fail silently - don't break tool execution if caching fails
            return null;
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
        return Files.exists(path.resolve("pom.xml")) ||
                Files.exists(path.resolve("build.gradle")) ||
                Files.exists(path.resolve("package.json")) ||
                Files.exists(path.resolve(".git")) ||
                Files.exists(path.resolve("src")) ||
                Files.exists(path.resolve("README.md"));
    }
}
