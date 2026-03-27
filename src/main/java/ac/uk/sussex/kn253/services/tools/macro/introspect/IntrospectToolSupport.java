package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.model.ProjectKnowledge;
import ac.uk.sussex.kn253.services.ToolActivityService;
import ac.uk.sussex.kn253.services.ToolActivityService.ToolActivityEvent;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.read.ReadToolSupport;

public class IntrospectToolSupport {

    private static final int MAX_FILE_CHARS = 3000;
    private static final int MAX_FOLDER_PATHS = 600;
    private static final int MAX_FOLDER_FILES_WITH_CONTENT = 24;
    private static final int MAX_FOLDER_FILE_CHARS = 1400;
    private static final int MAX_SOURCE_PATHS = 400;
    private static final int MAX_SOURCE_FILES_WITH_CONTENT = 40;
    private static final int MAX_SOURCE_FILE_CHARS = 2000;
    private static final int RECENT_TOOL_CALLS = 12;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> MANIFEST_FILENAMES = List.of(
            "README.md", "README.txt", "README.rst", "README", "readme.md",
            "package.json",
            "pom.xml",
            "build.gradle", "build.gradle.kts",
            "Cargo.toml",
            "go.mod",
            "requirements.txt", "pyproject.toml", "setup.py",
            "Makefile",
            "composer.json",
            "Gemfile");

    private static final List<String> SOURCE_FILE_EXTENSIONS = List.of(
            ".java", ".kt", ".scala",
            ".js", ".jsx", ".ts", ".tsx",
            ".py", ".rb", ".php", ".go", ".rs", ".cs", ".cpp", ".c", ".h",
            ".html", ".css", ".scss", ".md",
            ".json", ".yaml", ".yml", ".xml", ".toml", ".gradle", ".properties", ".sql", ".sh");

    private final WorkingDirectoryService workingDirectoryService;
    private final ToolActivityService toolActivityService;
    private final ReadToolSupport readToolSupport;

    public IntrospectToolSupport(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService) {
        this(workingDirectoryService, toolActivityService, new ReadToolSupport());
    }

    IntrospectToolSupport(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService,
            final ReadToolSupport readToolSupport) {
        this.workingDirectoryService = workingDirectoryService;
        this.toolActivityService = toolActivityService;
        this.readToolSupport = readToolSupport;
    }


    public String readFolderManifest(final Map<String, Object> args) {
        final Path dir = resolveDirectoryArg(args, "path");
        if (dir == null) {
            return "Not a directory: " + ToolArguments.getString(args, "path", "");
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Folder directory: ").append(dir).append("\n\n");

        final List<Path> allEntries;
        try (Stream<Path> stream = Files.walk(dir)) {
            allEntries = stream
                    .filter(path -> !path.equals(dir))
                    .sorted(Comparator.comparing(path -> dir.relativize(path).toString()))
                    .limit(MAX_FOLDER_PATHS)
                    .toList();
        } catch (final IOException e) {
            return "Failed to scan folder recursively for " + dir + ": " + e.getMessage();
        }

        if (allEntries.isEmpty()) {
            return "Folder directory: " + dir + "\n\nThe folder is empty.";
        }

        sb.append("=== folder entries (recursive) ===\n");
        for (final Path entry : allEntries) {
            final String rel = dir.relativize(entry).toString().replace('\\', '/');
            sb.append("- ").append(rel);
            if (Files.isDirectory(entry)) {
                sb.append("/");
            }
            sb.append("\n");
        }

        final List<Path> files = allEntries.stream().filter(Files::isRegularFile).toList();
        final List<Path> sampledFiles = sampleFolderFilesForContent(files);
        if (!sampledFiles.isEmpty()) {
            sb.append("\n=== sampled file contents (evidence only) ===\n");
            sb.append("Only files listed in this section were read for content. ")
                    .append("For all other files, content is unknown unless read via read_file.\n\n");

            for (final Path file : sampledFiles) {
                final String rel = dir.relativize(file).toString().replace('\\', '/');
                sb.append("--- ").append(rel).append(" ---\n");
                try {
                    final String content = Files.readString(file, StandardCharsets.UTF_8);
                    if (content.length() > MAX_FOLDER_FILE_CHARS) {
                        sb.append(content, 0, MAX_FOLDER_FILE_CHARS).append("\n...(truncated)");
                    } else {
                        sb.append(content);
                    }
                } catch (final IOException e) {
                    sb.append("(unreadable: ").append(e.getMessage()).append(")");
                }
                sb.append("\n\n");
            }

            if (files.size() > sampledFiles.size()) {
                sb.append("Content omitted for ")
                        .append(files.size() - sampledFiles.size())
                        .append(" file(s) in this folder tree. Use read_file for exact content when needed.\n");
            }
        }

        final String result = sb.toString().trim();

        // Cache the folder manifest result (best effort, don't break on failure)
        try {
            final Path projectDir = readToolSupport.resolveProjectDirectory(dir, null);
            readToolSupport.cacheFolderManifest(projectDir, dir, result);
        } catch (final Exception ignored) {
            // Caching failure should not break the tool
        }

        return result;
    }

    public String readProjectManifest(final Map<String, Object> args) {
        final Path dir = resolveDirectoryArg(args, "path");
        if (dir == null) {
            return "Not a directory: " + ToolArguments.getString(args, "path", "");
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Project directory: ").append(dir).append("\n\n");

        boolean found = false;
        for (final String filename : MANIFEST_FILENAMES) {
            final Path file = dir.resolve(filename);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                final String content = Files.readString(file, StandardCharsets.UTF_8);
                sb.append("=== ").append(filename).append(" ===\n");
                if (content.length() > MAX_FILE_CHARS) {
                    sb.append(content, 0, MAX_FILE_CHARS).append("\n...(truncated)");
                } else {
                    sb.append(content);
                }
                sb.append("\n\n");
                found = true;
            } catch (final IOException e) {
                sb.append("=== ").append(filename).append(" === (unreadable: ")
                        .append(e.getMessage()).append(")\n\n");
            }
        }

        if (!found) {
            sb.append("No standard project manifest files found in this directory.\n");
            sb.append("Consider using list_project_entries to see what files are present.");
        }

        appendSourceSummary(dir, sb);
        return sb.toString().trim();
    }

    public String readProjectKnowledge(final Map<String, Object> args) {
        final Path projectDirectory = resolveDirectoryArg(args, "projectDirectory");
        if (projectDirectory == null) {
            return "Not a directory: " + ToolArguments.getString(args, "projectDirectory", "");
        }

        final Project project = Project.find("directory", projectDirectory.toString()).firstResult();
        if (project == null) {
            return "No cached project knowledge found for " + projectDirectory;
        }

        final String tag = ToolArguments.getString(args, "tag", "").trim();
        if (!tag.isBlank()) {
            final ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project, tag);
            if (knowledge == null) {
                return "No cached project knowledge found for project=" + projectDirectory + " tag=" + tag;
            }
            return "projectDirectory=" + projectDirectory + "\n"
                    + "tag=" + tag + "\n"
                    + summarizeEntryCount(knowledge.getJsonContent()) + "\n\n"
                    + knowledge.getJsonContent();
        }

        final List<ProjectKnowledge> entries = ProjectKnowledge.<ProjectKnowledge>list("project", project).stream()
                .sorted(Comparator.comparing(ProjectKnowledge::getKey))
                .toList();
        if (entries.isEmpty()) {
            return "No cached project knowledge found for " + projectDirectory;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("projectDirectory=").append(projectDirectory).append("\n");
        sb.append("tags=").append(entries.size()).append("\n\n");
        sb.append("Cached tags:\n");
        for (final ProjectKnowledge entry : entries) {
            sb.append("- tag=").append(entry.getKey())
                    .append(" ")
                    .append(summarizeEntryCount(entry.getJsonContent()))
                    .append(" updatedAt=")
                    .append(entry.getUpdatedAt())
                    .append("\n");
        }
        sb.append("\nUse tag=<name> to read the full cached JSON for one tag.");
        return sb.toString();
    }

    public String getSessionContext() {
        final StringBuilder sb = new StringBuilder();
        sb.append("=== Current Session Context ===\n\n");

        final Path cwd = workingDirectoryService.getCurrentWorkingDirectory();
        sb.append("Working directory : ").append(cwd).append("\n");

        if (toolActivityService != null) {
            final List<ToolActivityEvent> recent = toolActivityService.recent(RECENT_TOOL_CALLS);
            if (recent.isEmpty()) {
                sb.append("\nNo tool calls recorded this session.\n");
            } else {
                sb.append("\nRecent tool calls (oldest → newest):\n");
                for (final ToolActivityEvent event : recent) {
                    sb.append("  [").append(event.timestamp()).append("] ")
                            .append(event.toolName())
                            .append("  args=").append(abbreviate(event.argumentsJson(), 100))
                            .append("\n");
                }
            }
        } else {
            sb.append("\n(Tool activity tracking not available)\n");
        }

        return sb.toString().trim();
    }

    public String openWorkspaceCanvas(final Map<String, Object> args) {
        final String raw = ToolArguments.getString(args, "path", "").trim();
        final Path base = workingDirectoryService.getCurrentWorkingDirectory();
        final Path target;
        if (raw.isBlank()) {
            target = base;
        } else {
            final Path candidate = Path.of(raw);
            target = (candidate.isAbsolute() ? candidate : base.resolve(candidate))
                    .normalize().toAbsolutePath();
        }
        return "canvas_open_request path=" + target;
    }

    public Path resolveDirectoryArg(final Map<String, Object> args, final String key) {
        final String raw = ToolArguments.getString(args, key, "").trim();
        final Path base = workingDirectoryService.getCurrentWorkingDirectory();
        final Path dir;
        if (raw.isBlank()) {
            dir = base;
        } else {
            final Path candidate = Path.of(raw);
            dir = (candidate.isAbsolute() ? candidate : base.resolve(candidate))
                    .normalize().toAbsolutePath();
        }
        if (!Files.isDirectory(dir)) {
            return null;
        }
        return dir;
    }

    private List<Path> sampleFolderFilesForContent(final List<Path> files) {
        final List<Path> sampled = new ArrayList<>();

        for (final Path file : files) {
            if (sampled.size() >= MAX_FOLDER_FILES_WITH_CONTENT) {
                break;
            }
            final String name = file.getFileName().toString().toLowerCase();
            final boolean keyName = name.equals("readme.md")
                    || name.equals("readme.txt")
                    || name.equals("readme")
                    || name.equals("package.json")
                    || name.equals("pom.xml")
                    || name.equals("build.gradle")
                    || name.equals("build.gradle.kts")
                    || name.equals("application.properties")
                    || name.equals("index.html")
                    || name.equals("main.ts")
                    || name.equals("main.tsx")
                    || name.equals("app.tsx")
                    || name.equals("app.java")
                    || name.equals("main.java");

            if (keyName) {
                sampled.add(file);
            }
        }

        if (sampled.size() >= MAX_FOLDER_FILES_WITH_CONTENT) {
            return sampled;
        }

        for (final Path file : files) {
            if (sampled.size() >= MAX_FOLDER_FILES_WITH_CONTENT) {
                break;
            }
            final String name = file.getFileName().toString().toLowerCase();
            if (hasLikelyTextExtension(name) && !sampled.contains(file)) {
                sampled.add(file);
            }
        }
        return sampled;
    }

    private void appendSourceSummary(final Path dir, final StringBuilder sb) {
        final Path srcDir = dir.resolve("src");
        if (!Files.isDirectory(srcDir)) {
            return;
        }

        final List<Path> sourceFiles;
        try (Stream<Path> stream = Files.walk(srcDir)) {
            sourceFiles = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> srcDir.relativize(path).toString()))
                    .limit(MAX_SOURCE_PATHS)
                    .toList();
        } catch (final IOException e) {
            sb.append("\n=== src/ (recursive) ===\n");
            sb.append("Failed to list src recursively: ").append(e.getMessage()).append("\n");
            return;
        }

        sb.append("\n=== src/ (recursive) ===\n");
        if (sourceFiles.isEmpty()) {
            sb.append("No files found under src/.\n");
            return;
        }

        for (final Path file : sourceFiles) {
            sb.append("- ").append(srcDir.relativize(file).toString().replace('\\', '/')).append("\n");
        }

        final List<Path> sampledFiles = sampleSourceFilesForContent(sourceFiles);
        if (sampledFiles.isEmpty()) {
            return;
        }

        sb.append("\n=== src/ sampled file contents (evidence only) ===\n");
        sb.append("Only files listed in this section were read for content. ")
                .append("For any file not listed here, content is unknown unless read via read_file.\n\n");

        int sampledCount = 0;
        for (final Path file : sampledFiles) {
            sampledCount++;
            final String relative = srcDir.relativize(file).toString().replace('\\', '/');
            sb.append("--- ").append(relative).append(" ---\n");
            try {
                final String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.length() > MAX_SOURCE_FILE_CHARS) {
                    sb.append(content, 0, MAX_SOURCE_FILE_CHARS).append("\n...(truncated)");
                } else {
                    sb.append(content);
                }
            } catch (final IOException e) {
                sb.append("(unreadable: ").append(e.getMessage()).append(")");
            }
            sb.append("\n\n");
        }

        if (sourceFiles.size() > sampledCount) {
            sb.append("Content omitted for ")
                    .append(sourceFiles.size() - sampledCount)
                    .append(" src file(s). Use read_file for exact content when needed.\n");
        }
    }

    private List<Path> sampleSourceFilesForContent(final List<Path> sourceFiles) {
        final List<Path> preferred = new ArrayList<>();
        for (final Path file : sourceFiles) {
            if (preferred.size() >= MAX_SOURCE_FILES_WITH_CONTENT) {
                break;
            }
            final String name = file.getFileName().toString().toLowerCase();
            final String fullPath = file.toString().toLowerCase();

            final boolean importantName = name.equals("main.java")
                    || name.equals("app.java")
                    || name.equals("application.java")
                    || name.equals("main.ts")
                    || name.equals("main.tsx")
                    || name.equals("index.ts")
                    || name.equals("index.tsx")
                    || name.equals("app.tsx")
                    || name.equals("module-info.java");

            final boolean inMain = fullPath.contains("/main/") || fullPath.contains("\\main\\");
            final boolean hasTextExtension = hasLikelyTextExtension(name);

            if ((importantName || inMain) && hasTextExtension) {
                preferred.add(file);
            }
        }

        if (preferred.size() >= MAX_SOURCE_FILES_WITH_CONTENT) {
            return preferred;
        }

        for (final Path file : sourceFiles) {
            if (preferred.size() >= MAX_SOURCE_FILES_WITH_CONTENT) {
                break;
            }
            final String name = file.getFileName().toString().toLowerCase();
            if (hasLikelyTextExtension(name) && !preferred.contains(file)) {
                preferred.add(file);
            }
        }
        return preferred;
    }

    private boolean hasLikelyTextExtension(final String filenameLowerCase) {
        for (final String ext : SOURCE_FILE_EXTENSIONS) {
            if (filenameLowerCase.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private String abbreviate(final String value, final int max) {
        if (value == null || value.isBlank()) {
            return "(none)";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private String summarizeEntryCount(final String rawJson) {
        try {
            final JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            final JsonNode entries = root.get("entries");
            if (entries != null && entries.isArray()) {
                return "entries=" + entries.size();
            }
        } catch (final IOException ignored) {
        }
        return "entries=unknown";
    }
}