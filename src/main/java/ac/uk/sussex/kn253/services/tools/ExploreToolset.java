package ac.uk.sussex.kn253.services.tools;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.services.ProjectDiscoveryService;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.*;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Toolset that gives the AI assistant the ability to explore the file system
 * and interact with the working-directory state.
 *
 * <p>
 * Provides thirteen tools:
 * <ul>
 * <li>{@code get_current_working_directory} – returns the assistant CWD</li>
 * <li>{@code change_working_directory} – navigates to a new directory</li>
 * <li>{@code resolve_path} – normalises an absolute or relative path</li>
 * <li>{@code search_paths} – bounded search for likely file or directory
 * targets when the user gives a vague filesystem reference</li>
 * <li>{@code get_path_info} – basic metadata (type, readable, writable)</li>
 * <li>{@code list_subdirectories} – immediate sub-folders</li>
 * <li>{@code list_files_recursive} – all files under a folder</li>
 * <li>{@code analyze_path_detailed} – detailed file/directory analysis</li>
 * <li>{@code summarize_path} – concise file/directory summary</li>
 * <li>{@code list_git_projects} – projects with git repositories</li>
 * <li>{@code list_github_projects} – projects with GitHub metadata</li>
 * <li>{@code list_project_entries} – files in a specific project directory</li>
 * <li>{@code get_git_log} – recent git commit log</li>
 * </ul>
 *
 * <p>
 * Legacy tool names are mapped to their canonical equivalents via
 * {@link #LEGACY_ALIASES} so that older conversation histories remain
 * compatible.
 * Path analysis is delegated to {@link PathAnalyzer}; argument parsing is
 * handled by {@link ToolArguments}.
 */
@ApplicationScoped
public class ExploreToolset implements ToolProvider, ToolExecutor {

    // -------------------------------------------------------------------------
    // Tool name constants
    // -------------------------------------------------------------------------

    static final String GET_CWD = "get_current_working_directory";
    static final String CHANGE_CWD = "change_working_directory";
    static final String RESOLVE_PATH = "resolve_path";
    static final String SEARCH_PATHS = "search_paths";
    static final String GET_PATH_INFO = "get_path_info";
    static final String LIST_SUBDIRECTORIES = "list_subdirectories";
    static final String LIST_FILES_RECURSIVE = "list_files_recursive";
    static final String ANALYZE_PATH = "analyze_path_detailed";
    static final String SUMMARIZE_PATH = "summarize_path";
    static final String LIST_GIT_PROJECTS = "list_git_projects";
    static final String LIST_GITHUB_PROJECTS = "list_github_projects";
    static final String LIST_PROJECT_ENTRIES = "list_project_entries";
    static final String GET_GIT_LOG = "get_git_log";

    private static final int DEFAULT_GIT_LOG_COUNT = 20;
    private static final int MAX_GIT_LOG_COUNT = 200;
    private static final int GIT_SCAN_DEPTH = 4;
    private static final int DEFAULT_SEARCH_DEPTH = 4;
    private static final int MAX_SEARCH_DEPTH = 8;
    private static final int DEFAULT_SEARCH_LIMIT = 12;
    private static final int MAX_SEARCH_LIMIT = 50;

    // -------------------------------------------------------------------------
    // Legacy name aliases (backwards-compatibility with old conversation history)
    // -------------------------------------------------------------------------

    private static final Map<String, String> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("get_cwd", GET_CWD),
            Map.entry("navigate_tool", CHANGE_CWD),
            Map.entry("find_paths", SEARCH_PATHS),
            Map.entry("path_info", GET_PATH_INFO),
            Map.entry("list_folders", LIST_SUBDIRECTORIES),
            Map.entry("list_folder", LIST_FILES_RECURSIVE),
            Map.entry("explain_tool", ANALYZE_PATH),
            Map.entry("summarise_tool", SUMMARIZE_PATH),
            Map.entry("list_files_in_project", LIST_PROJECT_ENTRIES),
            Map.entry("show_git_log", GET_GIT_LOG));

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final WorkingDirectoryService workingDirectoryService;
    private final List<ToolSpecification> toolSpecifications;

    @Inject
    Instance<PathSummaryLlmService> pathSummaryLlmService;

    @Inject
    Instance<ProjectDiscoveryService> projectDiscoveryService;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * No-arg CDI constructor. Uses {@link Arc} to lazily resolve the
     * {@link WorkingDirectoryService} bean, falling back to a plain instance for
     * unit-test environments where CDI is not running.
     */
    public ExploreToolset() {
        this(resolveWorkingDirectoryService());
    }

    /**
     * Primary constructor – injected by CDI.
     *
     * @param workingDirectoryService the service managing the assistant CWD.
     */
    @Inject
    public ExploreToolset(final WorkingDirectoryService workingDirectoryService) {
        this.workingDirectoryService = workingDirectoryService;
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
     * Returns {@code true} if this toolset can handle a tool with the given name,
     * including legacy aliases.
     *
     * @param toolName the raw tool name from the model response.
     */
    public boolean canHandle(final String toolName) {
        final String canonical = canonical(toolName);
        return toolSpecifications.stream().anyMatch(spec -> spec.name().equals(canonical));
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
            return dispatch(canonical(request.name()), args);
        } catch (final IllegalArgumentException e) {
            return "Invalid tool arguments: " + e.getMessage();
        } catch (final Exception e) {
            return "Tool execution failed for " + request.name() + ": " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    private String dispatch(final String toolName, final Map<String, Object> args) {
        return switch (toolName) {
            case GET_CWD -> getCwd();
            case CHANGE_CWD -> changeCwd(args);
            case RESOLVE_PATH -> resolvePath(args);
            case SEARCH_PATHS -> searchPaths(args);
            case GET_PATH_INFO -> PathAnalyzer.pathInfo(resolveArg(args, "path"));
            case LIST_SUBDIRECTORIES -> listSubdirectories(args);
            case LIST_FILES_RECURSIVE -> listFilesRecursive(args);
            case ANALYZE_PATH -> PathAnalyzer.analyze(resolveArg(args, "path"), true);
            case SUMMARIZE_PATH -> summarizePath(args);
            case LIST_GIT_PROJECTS -> listGitProjects();
            case LIST_GITHUB_PROJECTS -> listGithubProjects();
            case LIST_PROJECT_ENTRIES -> listProjectEntries(args);
            case GET_GIT_LOG -> getGitLog(args);
            default -> "Unknown tool: " + toolName;
        };
    }

    // -------------------------------------------------------------------------
    // Tool implementations
    // -------------------------------------------------------------------------

    private String getCwd() {
        return workingDirectoryService.getCurrentWorkingDirectory().toString();
    }

    private String changeCwd(final Map<String, Object> args) {
        final Path newCwd = workingDirectoryService.navigateTo(ToolArguments.require(args, "path"));
        return "cwd=" + newCwd;
    }

    private String resolvePath(final Map<String, Object> args) {
        return resolveArg(args, "path").toString();
    }

    private String searchPaths(final Map<String, Object> args) {
        final String query = ToolArguments.require(args, "query").trim();
        if (query.isBlank()) {
            return "Invalid query: expected a non-blank search term.";
        }

        final Path root = resolveArg(args, "path");
        if (!Files.isDirectory(root)) {
            return "Not a directory: " + root;
        }

        final int requestedDepth = ToolArguments.getInt(args, "maxDepth", DEFAULT_SEARCH_DEPTH);
        if (requestedDepth < 0 || requestedDepth > MAX_SEARCH_DEPTH) {
            return "Invalid maxDepth: expected a value between 0 and " + MAX_SEARCH_DEPTH + ".";
        }

        final int requestedLimit = ToolArguments.getInt(args, "limit", DEFAULT_SEARCH_LIMIT);
        if (requestedLimit < 1 || requestedLimit > MAX_SEARCH_LIMIT) {
            return "Invalid limit: expected a value between 1 and " + MAX_SEARCH_LIMIT + ".";
        }

        final boolean includeFiles = ToolArguments.getBoolean(args, "includeFiles", true);
        final boolean includeDirectories = ToolArguments.getBoolean(args, "includeDirectories", true);
        if (!includeFiles && !includeDirectories) {
            return "Invalid search options: at least one of includeFiles or includeDirectories must be true.";
        }

        final List<PathSearchMatch> matches = new ArrayList<>();
        final String queryLower = query.toLowerCase(Locale.ROOT);

        try {
            Files.walkFileTree(root, EnumSet.noneOf(java.nio.file.FileVisitOption.class), requestedDepth,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                            if (!dir.equals(root) && isIgnoredDirectory(dir)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (!dir.equals(root) && includeDirectories) {
                                addMatch(root, dir, true, queryLower, matches);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            if (includeFiles) {
                                addMatch(root, file, false, queryLower, matches);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (final IOException e) {
            return "Failed to search paths under " + root + ": " + e.getMessage();
        }

        matches.sort(Comparator
                .comparingInt(PathSearchMatch::matchRank)
                .thenComparing((final PathSearchMatch match) -> match.directory() ? 0 : 1)
                .thenComparingInt(match -> match.relativePath().length())
                .thenComparing(PathSearchMatch::relativePath, String.CASE_INSENSITIVE_ORDER));

        final List<PathSearchMatch> limited = matches.stream().limit(requestedLimit).toList();
        final StringBuilder sb = new StringBuilder();
        sb.append("searchRoot=").append(root.toAbsolutePath().normalize()).append("\n");
        sb.append("query=").append(query).append("\n");
        sb.append("maxDepth=").append(requestedDepth).append("\n");
        sb.append("includeFiles=").append(includeFiles).append("\n");
        sb.append("includeDirectories=").append(includeDirectories).append("\n");

        if (limited.isEmpty()) {
            sb.append("matches=0\n");
            sb.append("No matching paths found.");
            return sb.toString();
        }

        sb.append("matches=").append(limited.size());
        if (matches.size() > limited.size()) {
            sb.append(" (truncated from ").append(matches.size()).append(")");
        }
        sb.append("\n\n");
        sb.append("Candidate paths:\n");
        for (final PathSearchMatch match : limited) {
            sb.append("- type=").append(match.directory() ? "directory" : "file")
                    .append(" match=").append(match.matchKind())
                    .append(" relative=").append(match.relativePath())
                    .append(" path=").append(match.absolutePath())
                    .append("\n");
        }
        sb.append(
                "\nUse these candidates to ask the user which target they mean before navigating when multiple plausible matches exist.");
        return sb.toString();
    }

    private String listSubdirectories(final Map<String, Object> args) {
        final Path path = resolveArg(args, "path");
        if (!Files.isDirectory(path)) {
            return "Not a directory: " + path;
        }
        try {
            final List<String> folders = Files.list(path)
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
            if (folders.isEmpty()) {
                return "path=" + path + "\nNo folders found in " + path;
            }
            return "path=" + path + "\n" + String.join("\n", folders);
        } catch (final IOException e) {
            return "Failed to list folders for " + path + ": " + e.getMessage();
        }
    }

    private String listFilesRecursive(final Map<String, Object> args) {
        final Path path = resolveArg(args, "path");
        if (!Files.isDirectory(path)) {
            return "Not a directory: " + path;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            final List<String> files = stream
                    .filter(Files::isRegularFile)
                    .map(file -> path.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
            if (files.isEmpty()) {
                return "path=" + path + "\nNo files found in " + path;
            }
            return "path=" + path + "\n" + String.join("\n", files);
        } catch (final IOException e) {
            return "Failed to list files for " + path + ": " + e.getMessage();
        }
    }

    private String summarizePath(final Map<String, Object> args) {
        final Path path = resolveArg(args, "path");
        if (pathSummaryLlmService != null && pathSummaryLlmService.isResolvable()) {
            try {
                return pathSummaryLlmService.get().summarizePath(path);
            } catch (final Exception e) {
                return "Failed to summarize path via LLM for " + path + ": " + e.getMessage();
            }
        }
        // Fallback for non-CDI/unit-test environments.
        return PathAnalyzer.analyze(path, false);
    }

    private String listGitProjects() {
        discoverProjectsFromCwdSafely();
        final List<Project> dbProjects = gitProjectsFromDb();
        if (!dbProjects.isEmpty()) {
            return dbProjects.stream()
                    .map(p -> "- " + p.getDirectory())
                    .collect(Collectors.joining("\n"));
        }
        final List<String> fsProjects = gitProjectsFromFilesystem();
        if (fsProjects.isEmpty()) {
            return "No git projects found.";
        }
        return fsProjects.stream().map(p -> "- " + p).collect(Collectors.joining("\n"));
    }

    private String listGithubProjects() {
        discoverProjectsFromCwdSafely();
        final List<Project> projects = githubProjectsFromDb();
        if (projects.isEmpty()) {
            return "No GitHub projects found in database.";
        }
        return projects.stream()
                .map(p -> "- " + p.getDirectory() + " -> " + p.getGithubRepository().getName())
                .collect(Collectors.joining("\n"));
    }

    private void discoverProjectsFromCwdSafely() {
        if (projectDiscoveryService == null || !projectDiscoveryService.isResolvable()) {
            return;
        }
        try {
            projectDiscoveryService.get().discoverFromCwd();
        } catch (final Exception ignored) {
            // Discovery is best-effort; listing still falls back to filesystem scan.
        }
    }

    private String listProjectEntries(final Map<String, Object> args) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final String relativePath = ToolArguments.getString(args, "relativePath", "");
        final Path target = relativePath.isBlank()
                ? project
                : project.resolve(relativePath).normalize();

        if (!target.startsWith(project)) {
            return "Invalid relativePath: outside project directory.";
        }
        if (!Files.isDirectory(target)) {
            return "Not a directory: " + target;
        }
        try {
            final List<String> entries = Files.list(target)
                    .sorted()
                    .map(p -> {
                        final String name = p.getFileName().toString();
                        return Files.isDirectory(p) ? name + "/" : name;
                    })
                    .toList();
            if (entries.isEmpty()) {
                return "No entries found in " + target;
            }
            return String.join("\n", entries);
        } catch (final IOException e) {
            return "Failed to list files for " + target + ": " + e.getMessage();
        }
    }

    private String getGitLog(final Map<String, Object> args) {
        final Path repoPath = resolveArg(args, "path");
        final int maxCount = ToolArguments.getInt(args, "maxCount", DEFAULT_GIT_LOG_COUNT);
        if (maxCount < 1 || maxCount > MAX_GIT_LOG_COUNT) {
            return "Invalid maxCount: expected a value between 1 and " + MAX_GIT_LOG_COUNT + ".";
        }
        try {
            final Process process = new ProcessBuilder(
                    "git", "-C", repoPath.toString(), "log", "--oneline", "-n", String.valueOf(maxCount))
                    .start();
            final String stdout = new String(process.getInputStream().readAllBytes()).trim();
            final String stderr = new String(process.getErrorStream().readAllBytes()).trim();
            final int exitCode = process.waitFor();

            if (exitCode != 0) {
                return "Failed to get git log for " + repoPath + ": "
                        + (stderr.isBlank() ? "Unknown git error." : stderr);
            }
            if (stdout.isBlank()) {
                return "No commits found in " + repoPath;
            }
            return "path=" + repoPath.toAbsolutePath().normalize() + "\n" + stdout;
        } catch (final Exception e) {
            return "Failed to get git log for " + repoPath + ": " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Database / filesystem project helpers
    // -------------------------------------------------------------------------

    private List<Project> gitProjectsFromDb() {
        if (!isCdiAvailable()) {
            return List.of();
        }
        try {
            return Project.<Project>listAll().stream()
                    .filter(p -> p.getGitRepository() != null)
                    .sorted(Comparator.comparing(Project::getDirectory, Comparator.nullsLast(String::compareTo)))
                    .toList();
        } catch (final RuntimeException ignored) {
            return List.of();
        }
    }

    private List<Project> githubProjectsFromDb() {
        if (!isCdiAvailable()) {
            return List.of();
        }
        try {
            return Project.<Project>listAll().stream()
                    .filter(p -> p.getGithubRepository() != null)
                    .sorted(Comparator.comparing(Project::getDirectory, Comparator.nullsLast(String::compareTo)))
                    .toList();
        } catch (final RuntimeException ignored) {
            return List.of();
        }
    }

    private List<String> gitProjectsFromFilesystem() {
        final Path cwd = workingDirectoryService.getCurrentWorkingDirectory();
        try (Stream<Path> stream = Files.walk(cwd, GIT_SCAN_DEPTH)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> !isIgnoredDirectory(path))
                    .filter(path -> Files.isDirectory(path.resolve(".git")))
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .sorted()
                    .toList();
        } catch (final IOException ignored) {
            return List.of();
        }
    }

    private static boolean isIgnoredDirectory(final Path path) {
        final String name = path.getFileName().toString();
        return name.equals(".git")
                || name.equals("node_modules")
                || name.equals("target")
                || name.equals("build")
                || name.equals(".idea")
                || name.equals(".vscode");
    }

    private static void addMatch(
            final Path root,
            final Path candidate,
            final boolean directory,
            final String queryLower,
            final List<PathSearchMatch> matches) {
        final Path fileNamePath = candidate.getFileName();
        if (fileNamePath == null) {
            return;
        }
        final String fileName = fileNamePath.toString();
        final String fileNameLower = fileName.toLowerCase(Locale.ROOT);
        final String matchKind = classifyMatch(fileNameLower, queryLower);
        if (matchKind == null) {
            return;
        }
        final String relative = root.relativize(candidate).toString().replace('\\', '/');
        matches.add(new PathSearchMatch(
                candidate.toAbsolutePath().normalize().toString(),
                relative.isBlank() ? "." : relative,
                directory,
                matchKind,
                matchRank(matchKind)));
    }

    private static String classifyMatch(final String candidateNameLower, final String queryLower) {
        if (candidateNameLower.equals(queryLower)) {
            return "exact";
        }
        if (candidateNameLower.startsWith(queryLower)) {
            return "prefix";
        }
        if (candidateNameLower.contains(queryLower)) {
            return "substring";
        }
        return null;
    }

    private static int matchRank(final String matchKind) {
        return switch (matchKind) {
            case "exact" -> 0;
            case "prefix" -> 1;
            default -> 2;
        };
    }

    // -------------------------------------------------------------------------
    // Path resolution helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the {@code key} argument from {@code args} against the current CWD.
     * Falls back to the CWD itself when the argument is absent or blank.
     *
     * @param args the parsed argument map.
     * @param key  the argument name to look up.
     * @return an absolute, normalised path.
     */
    private Path resolveArg(final Map<String, Object> args, final String key) {
        final String raw = ToolArguments.getString(args, key, "").trim();
        final Path base = workingDirectoryService.getCurrentWorkingDirectory();
        if (raw.isBlank()) {
            return base;
        }
        final Path candidate = Path.of(raw);
        return (candidate.isAbsolute() ? candidate : base.resolve(candidate))
                .normalize()
                .toAbsolutePath();
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the canonical tool name for {@code toolName}, applying legacy
     * aliases where appropriate.
     *
     * @param toolName the raw name from the model response; may be {@code null}.
     * @return the canonical name, or an empty string for null/blank input.
     */
    private static String canonical(final String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "";
        }
        return LEGACY_ALIASES.getOrDefault(toolName, toolName);
    }

    private static boolean isCdiAvailable() {
        return Arc.container() != null && Arc.container().isRunning();
    }

    /**
     * Resolves the {@link WorkingDirectoryService} from CDI when available,
     * or creates a plain instance for unit-test environments where CDI is absent.
     */
    private static WorkingDirectoryService resolveWorkingDirectoryService() {
        if (isCdiAvailable()) {
            final var instance = Arc.container().instance(WorkingDirectoryService.class);
            if (instance != null && instance.isAvailable()) {
                return instance.get();
            }
        }
        return new WorkingDirectoryService();
    }

    // -------------------------------------------------------------------------
    // Tool specification builders
    // -------------------------------------------------------------------------

    private static List<ToolSpecification> buildSpecifications() {
        return List.of(
                spec(GET_CWD, "Return the current working directory as an absolute path.", null, null),
                spec(CHANGE_CWD,
                        "Change the assistant working directory only when the user explicitly asks to navigate or switch folders. Supports absolute paths or paths relative to cwd.",
                        new String[] { "path" }, new String[] { "Directory path to navigate to" }),
                spec(RESOLVE_PATH,
                        "Resolve an absolute or relative path against cwd and return the normalized absolute path.",
                        new String[] { "path" }, new String[] { "Absolute or relative path" }),
                specSearchPaths(),
                spec(GET_PATH_INFO,
                        "Return basic metadata for a path (exists, type, readability, writability, absolute path).",
                        new String[] { "path" }, new String[] { "Absolute or relative path" }),
                spec(LIST_SUBDIRECTORIES,
                        "List immediate sub-folders for a given absolute or relative path.",
                        new String[] { "path" }, new String[] { "Directory path to inspect" }),
                spec(LIST_FILES_RECURSIVE,
                        "List all files under a given folder recursively using paths relative to that folder.",
                        new String[] { "path" }, new String[] { "Directory path to inspect" }),
                spec(ANALYZE_PATH,
                        "Provide a detailed analysis of a file or directory path.",
                        new String[] { "path" }, new String[] { "File or directory path to analyze" }),
                spec(SUMMARIZE_PATH,
                        "Provide a concise summary of a file or directory path.",
                        new String[] { "path" }, new String[] { "File or directory path to summarize" }),
                spec(LIST_GIT_PROJECTS,
                        "List known projects in the database that have a Git repository attached.", null, null),
                spec(LIST_GITHUB_PROJECTS,
                        "List known projects in the database that have GitHub repository metadata attached.", null,
                        null),
                specProjectEntries(),
                specGitLog());
    }

    /**
     * Builds a tool specification with optional string parameters.
     *
     * @param name              canonical tool name.
     * @param description       tool description.
     * @param paramNames        parameter names; {@code null} means no parameters.
     * @param paramDescriptions parameter descriptions aligned with
     *                          {@code paramNames}.
     */
    private static ToolSpecification spec(
            final String name,
            final String description,
            final String[] paramNames,
            final String[] paramDescriptions) {
        final JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                schemaBuilder.addProperty(paramNames[i],
                        JsonStringSchema.builder().description(paramDescriptions[i]).build());
            }
            schemaBuilder.required(paramNames);
        }
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(schemaBuilder.build())
                .build();
    }

    /** Builds the {@code list_project_entries} tool specification. */
    private static ToolSpecification specSearchPaths() {
        return ToolSpecification.builder()
                .name(SEARCH_PATHS)
                .description(
                        "Search from a directory for likely file or folder candidates matching a partial name. " +
                                "Use this first when the user refers to a vague filesystem target such as frontend, webui, tests, config, or main entry point and you do not yet know the exact path. "
                                +
                                "Do not navigate automatically when multiple plausible matches are returned; summarize the candidates and ask the user to choose.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("query",
                                JsonStringSchema.builder()
                                        .description("Required partial file or directory name to search for.")
                                        .build())
                        .addProperty("path",
                                JsonStringSchema.builder()
                                        .description(
                                                "Optional search root directory. Defaults to the current working directory.")
                                        .build())
                        .addProperty("maxDepth",
                                JsonStringSchema.builder()
                                        .description("Optional maximum search depth from the root (0-8, default 4).")
                                        .build())
                        .addProperty("limit",
                                JsonStringSchema.builder()
                                        .description("Optional maximum number of matches to return (1-50, default 12).")
                                        .build())
                        .addProperty("includeFiles",
                                JsonStringSchema.builder()
                                        .description("Optional boolean flag to include file matches. Defaults to true.")
                                        .build())
                        .addProperty("includeDirectories",
                                JsonStringSchema.builder()
                                        .description(
                                                "Optional boolean flag to include directory matches. Defaults to true.")
                                        .build())
                        .required("query")
                        .build())
                .build();
    }

    /** Builds the {@code list_project_entries} tool specification. */
    private static ToolSpecification specProjectEntries() {
        return ToolSpecification.builder()
                .name(LIST_PROJECT_ENTRIES)
                .description("List files and folders in a project's directory, optionally under a relative subpath.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory",
                                JsonStringSchema.builder()
                                        .description("Absolute path to the project root directory").build())
                        .addProperty("relativePath",
                                JsonStringSchema.builder()
                                        .description("Optional sub-directory inside the project").build())
                        .required("projectDirectory")
                        .build())
                .build();
    }

    /** Builds the {@code get_git_log} tool specification. */
    private static ToolSpecification specGitLog() {
        return ToolSpecification.builder()
                .name(GET_GIT_LOG)
                .description("Return recent git commits (one line per commit) for a repository.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder()
                                        .description("Optional repository path. Defaults to current working directory.")
                                        .build())
                        .addProperty("maxCount",
                                JsonStringSchema.builder()
                                        .description("Optional number of commits to return (1-200, default 20).")
                                        .build())
                        .build())
                .build();
    }

    private record PathSearchMatch(
            String absolutePath,
            String relativePath,
            boolean directory,
            String matchKind,
            int matchRank) {
    }
}
