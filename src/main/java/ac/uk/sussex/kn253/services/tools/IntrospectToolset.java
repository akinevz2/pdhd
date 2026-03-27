package ac.uk.sussex.kn253.services.tools;

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
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.*;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Toolset that gives the AI assistant self-reflection and project discovery
 * capabilities.
 *
 * <p>
 * Provides four tools:
 * <ul>
 * <li>{@code read_folder_manifest} – reads a specific folder (not
 * whole-project)
 * and returns an evidence-based recursive listing plus sampled file contents
 * for
 * files under that folder only.</li>
 * <li>{@code read_project_manifest} – reads key project identity files
 * (README, package.json, pom.xml, Cargo.toml, go.mod, etc.) and also indexes
 * source files recursively under {@code src} with explicit sampled file
 * contents
 * to understand the project's purpose and technology stack.</li>
 * <li>{@code read_project_knowledge} – recalls cached tagged notes for a
 * project so the assistant can reuse earlier findings and user
 * constraints.</li>
 * <li>{@code get_session_context} – returns the current working directory and
 * recent tool call history so the assistant can reflect on where it is
 * and what it has already done.</li>
 * <li>{@code open_workspace_canvas} – requests opening a project canvas in the
 * web UI for a given absolute or relative path.</li>
 * </ul>
 */
@ApplicationScoped
public class IntrospectToolset implements ToolProvider, ToolExecutor {

    // -------------------------------------------------------------------------
    // Tool name constants
    // -------------------------------------------------------------------------

    static final String READ_PROJECT_MANIFEST = "read_project_manifest";
    static final String READ_FOLDER_MANIFEST = "read_folder_manifest";
    static final String READ_PROJECT_KNOWLEDGE = "read_project_knowledge";
    static final String GET_SESSION_CONTEXT = "get_session_context";
    static final String OPEN_WORKSPACE_CANVAS = "open_workspace_canvas";

    private static final int MAX_FILE_CHARS = 3000;
    private static final int MAX_FOLDER_PATHS = 600;
    private static final int MAX_FOLDER_FILES_WITH_CONTENT = 24;
    private static final int MAX_FOLDER_FILE_CHARS = 1400;
    private static final int MAX_SOURCE_PATHS = 400;
    private static final int MAX_SOURCE_FILES_WITH_CONTENT = 40;
    private static final int MAX_SOURCE_FILE_CHARS = 2000;
    private static final int RECENT_TOOL_CALLS = 12;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Ordered list of well-known filenames that describe a project's identity.
     * Files are read in this order; all that exist are included.
     */
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

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final WorkingDirectoryService workingDirectoryService;
    private final ToolActivityService toolActivityService;
    private final List<ToolSpecification> toolSpecifications;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * No-arg CDI constructor. Uses {@link Arc} to resolve dependencies, falling
     * back to plain instances for unit-test environments without CDI.
     */
    public IntrospectToolset() {
        this(resolveWorkingDirectoryService(), resolveToolActivityService());
    }

    /**
     * Primary constructor – injected by CDI.
     *
     * @param workingDirectoryService the service managing the assistant CWD.
     * @param toolActivityService     the ring-buffer of recent tool calls.
     */
    @Inject
    public IntrospectToolset(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService) {
        this.workingDirectoryService = workingDirectoryService;
        this.toolActivityService = toolActivityService;
        this.toolSpecifications = buildSpecifications();
    }

    // -------------------------------------------------------------------------
    // ToolProvider / ToolExecutor API
    // -------------------------------------------------------------------------

    /** Returns all tool specifications exposed by this toolset. */
    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    /**
     * Returns {@code true} when this toolset can handle the given tool name.
     *
     * @param toolName the raw tool name from the model response.
     */
    public boolean canHandle(final String toolName) {
        return READ_FOLDER_MANIFEST.equals(toolName)
                || READ_PROJECT_MANIFEST.equals(toolName)
                || READ_PROJECT_KNOWLEDGE.equals(toolName)
                || GET_SESSION_CONTEXT.equals(toolName)
                || OPEN_WORKSPACE_CANVAS.equals(toolName);
    }

    /** {@inheritDoc} */
    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest request) {
        final ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (final ToolSpecification spec : toolSpecifications) {
            builder.add(spec, this);
        }
        return builder.build();
    }

    /** {@inheritDoc} */
    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        try {
            final Map<String, Object> args = ToolArguments.parse(request.arguments());
            return switch (request.name()) {
                case READ_FOLDER_MANIFEST -> readFolderManifest(args);
                case READ_PROJECT_MANIFEST -> readProjectManifest(args);
                case READ_PROJECT_KNOWLEDGE -> readProjectKnowledge(args);
                case GET_SESSION_CONTEXT -> getSessionContext();
                case OPEN_WORKSPACE_CANVAS -> openWorkspaceCanvas(args);
                default -> "Unknown tool for IntrospectToolset: " + request.name();
            };
        } catch (final IllegalArgumentException e) {
            return "Invalid tool arguments: " + e.getMessage();
        } catch (final Exception e) {
            return "Tool execution failed for " + request.name() + ": " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Tool implementations
    // -------------------------------------------------------------------------

    /**
     * Reads a folder recursively and returns a grounded manifest of discovered
     * entries plus sampled file contents from files under that folder only.
     */
    private String readFolderManifest(final Map<String, Object> args) {
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
            if (Files.isDirectory(entry)) {
                sb.append("- ").append(rel).append("/\n");
            } else {
                sb.append("- ").append(rel).append("\n");
            }
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

        return sb.toString().trim();
    }

    /**
     * Reads well-known project identity files from {@code path} (or the CWD when
     * path is omitted) and returns their combined content.
     */
    private String readProjectManifest(final Map<String, Object> args) {
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

    private Path resolveDirectoryArg(final Map<String, Object> args, final String key) {
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

    private static boolean hasLikelyTextExtension(final String filenameLowerCase) {
        for (final String ext : SOURCE_FILE_EXTENSIONS) {
            if (filenameLowerCase.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the current session context: CWD and recent tool call history.
     * Designed to help the assistant orient itself at the start of a task or
     * after navigating to a new location.
     */
    private String getSessionContext() {
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

    /**
     * Requests opening a workspace canvas for the provided path.
     *
     * <p>
     * The frontend observes this tool-call activity and performs the actual UI
     * action. Keeping this as a tool call ensures the user's intent is visible in
     * activity logs.
     */
    private String openWorkspaceCanvas(final Map<String, Object> args) {
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

    private String readProjectKnowledge(final Map<String, Object> args) {
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String abbreviate(final String s, final int max) {
        if (s == null || s.isBlank()) {
            return "(none)";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String summarizeEntryCount(final String rawJson) {
        try {
            final JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            final JsonNode entries = root.get("entries");
            if (entries != null && entries.isArray()) {
                return "entries=" + entries.size();
            }
        } catch (final IOException ignored) {
            // Fall back below.
        }
        return "entries=unknown";
    }

    // -------------------------------------------------------------------------
    // Tool specification builders
    // -------------------------------------------------------------------------

    private static List<ToolSpecification> buildSpecifications() {
        return List.of(
                ToolSpecification.builder()
                        .name(READ_FOLDER_MANIFEST)
                        .description(
                                "Read a specific folder recursively and return an evidence-based folder manifest " +
                                        "(discovered files/folders + sampled exact contents from files in that folder tree).\n"
                                        +
                                        "Use this when the user asks to summarise a folder or subfolder.\n" +
                                        "Do NOT use this as a whole-project summary tool; use read_project_manifest for that.\n"
                                        +
                                        "Do not claim content for files not included in the sampled-content section.")
                        .parameters(JsonObjectSchema.builder()
                                .addProperty("path",
                                        JsonStringSchema.builder()
                                                .description(
                                                        "Folder path to scan recursively (absolute or relative to cwd). "
                                                                +
                                                                "If omitted, uses the current working directory.")
                                                .build())
                                .build())
                        .build(),

                ToolSpecification.builder()
                        .name(READ_PROJECT_MANIFEST)
                        .description(
                                "Read key project identity files (README, package.json, pom.xml, Cargo.toml, " +
                                        "go.mod, requirements.txt, Makefile, etc.) from a directory and inspect src/ " +
                                        "recursively (file list + sampled exact contents) to understand " +
                                        "the project's purpose and technology stack.\n" +
                                        "Use ONLY for summarising or explaining the entire project.\n" +
                                        "Do NOT use for summarising individual files or folders—use read_folder_manifest (preferred) or the 'summarize_path' tool from ExploreToolset for those cases.\n"
                                        +
                                        "If the user asks to summarise a folder or file, do NOT call this tool.\n" +
                                        "Parameter 'path' is required unless using the current working directory.")
                        .parameters(JsonObjectSchema.builder()
                                .addProperty("path",
                                        JsonStringSchema.builder()
                                                .description(
                                                        "Directory to scan (absolute or relative to cwd). " +
                                                                "If omitted, uses the current working directory.")
                                                .build())
                                .build())
                        .build(),

                ToolSpecification.builder()
                        .name(READ_PROJECT_KNOWLEDGE)
                        .description(
                                "Read cached tagged project knowledge remembered from earlier user queries or prior analysis. "
                                        +
                                        "Use this to recall stored constraints, decisions, requirements, or bug notes before repeating work.")
                        .parameters(JsonObjectSchema.builder()
                                .addProperty("projectDirectory",
                                        JsonStringSchema.builder()
                                                .description(
                                                        "Project directory to read knowledge for (absolute or relative to cwd).")
                                                .build())
                                .addProperty("tag",
                                        JsonStringSchema.builder()
                                                .description(
                                                        "Optional knowledge tag to read in full. If omitted, returns available tags.")
                                                .build())
                                .required("projectDirectory")
                                .build())
                        .build(),

                ToolSpecification.builder()
                        .name(GET_SESSION_CONTEXT)
                        .description(
                                "Return the current working directory and the recent tool call history for this session.\n"
                                        +
                                        "Use this to reflect on your current context, especially at the start of a new task or after changing directories.")
                        .parameters(JsonObjectSchema.builder().build())
                        .build(),

                ToolSpecification.builder()
                        .name(OPEN_WORKSPACE_CANVAS)
                        .description(
                                "Request opening a workspace project canvas in the web UI for a given path.\n" +
                                        "Use only when the user explicitly asks to open a project/folder/file in canvas.\n"
                                        +
                                        "Parameter 'path' is required unless using the current working directory.")
                        .parameters(JsonObjectSchema.builder()
                                .addProperty("path",
                                        JsonStringSchema.builder()
                                                .description(
                                                        "Target path to open in canvas (file or directory). " +
                                                                "If omitted, uses the current working directory.")
                                                .build())
                                .build())
                        .build());
    }

    // -------------------------------------------------------------------------
    // CDI resolution helpers
    // -------------------------------------------------------------------------

    private static boolean isCdiAvailable() {
        return Arc.container() != null && Arc.container().isRunning();
    }

    private static WorkingDirectoryService resolveWorkingDirectoryService() {
        if (isCdiAvailable()) {
            final var instance = Arc.container().instance(WorkingDirectoryService.class);
            if (instance != null && instance.isAvailable()) {
                return instance.get();
            }
        }
        return new WorkingDirectoryService();
    }

    private static ToolActivityService resolveToolActivityService() {
        if (isCdiAvailable()) {
            final var instance = Arc.container().instance(ToolActivityService.class);
            if (instance != null && instance.isAvailable()) {
                return instance.get();
            }
        }
        return null;
    }
}
