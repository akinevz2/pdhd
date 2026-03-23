package ac.uk.sussex.kn253.services.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.*;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ExploreToolset implements ToolProvider, ToolExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GET_CWD = "get_current_working_directory";
    private static final String CHANGE_CWD = "change_working_directory";
    private static final String RESOLVE_PATH = "resolve_path";
    private static final String GET_PATH_INFO = "get_path_info";
    private static final String LIST_SUBDIRECTORIES = "list_subdirectories";
    private static final String LIST_FILES_RECURSIVE = "list_files_recursive";
    private static final String ANALYZE_PATH_DETAILED = "analyze_path_detailed";
    private static final String SUMMARIZE_PATH = "summarize_path";
    private static final String LIST_GIT_PROJECTS = "list_git_projects";
    private static final String LIST_GITHUB_PROJECTS = "list_github_projects";
    private static final String LIST_PROJECT_ENTRIES = "list_project_entries";
    private static final String GET_GIT_LOG = "get_git_log";
    private static final int DEFAULT_GIT_LOG_COUNT = 20;
    private static final int MAX_GIT_LOG_COUNT = 200;

    private static final Map<String, String> LEGACY_TOOL_NAME_ALIASES = Map.ofEntries(
            Map.entry("get_cwd", GET_CWD),
            Map.entry("navigate_tool", CHANGE_CWD),
            Map.entry("path_info", GET_PATH_INFO),
            Map.entry("list_folders", LIST_SUBDIRECTORIES),
            Map.entry("list_folder", LIST_FILES_RECURSIVE),
            Map.entry("explain_tool", ANALYZE_PATH_DETAILED),
            Map.entry("summarise_tool", SUMMARIZE_PATH),
            Map.entry("list_files_in_project", LIST_PROJECT_ENTRIES),
            Map.entry("show_git_log", GET_GIT_LOG));

    private final WorkingDirectoryService workingDirectoryService;
    private final List<ToolSpecification> toolSpecifications;

    public ExploreToolset() {
        this(resolveWorkingDirectoryService());
    }

    @Inject
    public ExploreToolset(final WorkingDirectoryService workingDirectoryService) {
        this.workingDirectoryService = workingDirectoryService;
        this.toolSpecifications = List.of(
                getCwdSpec(),
                navigateToolSpec(),
                resolvePathSpec(),
                pathInfoSpec(),
                listFoldersSpec(),
                listFolderSpec(),
                explainToolSpec(),
                summariseToolSpec(),
                listGitProjectsSpec(),
                listGithubProjectsSpec(),
                listFilesInProjectSpec(),
                getGitLogSpec());
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    public boolean canHandle(final String toolName) {
        final String canonicalToolName = canonicalToolName(toolName);
        return toolSpecifications.stream().anyMatch(spec -> spec.name().equals(canonicalToolName));
    }

    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest arg0) {
        final ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (final ToolSpecification spec : toolSpecifications) {
            builder.add(spec, this);
        }
        return builder.build();
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        try {
            final Map<String, Object> args = parseArgs(request.arguments());
            final String toolName = canonicalToolName(request.name());
            return switch (toolName) {
                case GET_CWD -> getCwd();
                case CHANGE_CWD -> changeWorkingDirectory(args);
                case RESOLVE_PATH -> resolvePath(args);
                case GET_PATH_INFO -> pathInfo(args);
                case LIST_SUBDIRECTORIES -> listFolders(args);
                case LIST_FILES_RECURSIVE -> listFolder(args);
                case ANALYZE_PATH_DETAILED -> explainPath(args, true);
                case SUMMARIZE_PATH -> explainPath(args, false);
                case LIST_GIT_PROJECTS -> listGitProjects();
                case LIST_GITHUB_PROJECTS -> listGithubProjects();
                case LIST_PROJECT_ENTRIES -> listFilesInProject(args);
                case GET_GIT_LOG -> getGitLog(args);
                default -> "Unknown tool: " + request.name();
            };
        } catch (final IllegalArgumentException e) {
            return "Invalid tool arguments: " + e.getMessage();
        } catch (final Exception e) {
            return "Tool execution failed for " + request.name() + ": " + e.getMessage();
        }
    }

    private String canonicalToolName(final String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "";
        }
        return LEGACY_TOOL_NAME_ALIASES.getOrDefault(toolName, toolName);
    }

    private ToolSpecification getCwdSpec() {
        return ToolSpecification.builder()
                .name(GET_CWD)
                .description("Return the current working directory as an absolute path.")
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    private ToolSpecification navigateToolSpec() {
        return ToolSpecification.builder()
                .name(CHANGE_CWD)
                .description(
                        "Change the assistant working directory. Supports absolute paths or paths relative to current working directory.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("Directory path to navigate to").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification resolvePathSpec() {
        return ToolSpecification.builder()
                .name(RESOLVE_PATH)
                .description(
                        "Resolve an absolute or relative path against cwd and return the normalized absolute path.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("Absolute or relative path").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification pathInfoSpec() {
        return ToolSpecification.builder()
                .name(GET_PATH_INFO)
                .description(
                        "Return basic metadata for a path (exists, type, readability, writability, absolute path).")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("Absolute or relative path").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification listFoldersSpec() {
        return ToolSpecification.builder()
                .name(LIST_SUBDIRECTORIES)
                .description("List immediate sub-folders for a given absolute or relative path.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("Directory path to inspect").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification listFolderSpec() {
        return ToolSpecification.builder()
                .name(LIST_FILES_RECURSIVE)
                .description("List all files under a given folder recursively using paths relative to that folder.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("Directory path to inspect").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification explainToolSpec() {
        return ToolSpecification.builder()
                .name(ANALYZE_PATH_DETAILED)
                .description(
                        "Provide a detailed analysis of a file or directory path to help understand its contents and structure.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("File or directory path to analyze").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification summariseToolSpec() {
        return ToolSpecification.builder()
                .name(SUMMARIZE_PATH)
                .description(
                        "Provide a concise summary of a file or directory path so the model can quickly understand what it contains.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("File or directory path to summarize").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification listGitProjectsSpec() {
        return ToolSpecification.builder()
                .name(LIST_GIT_PROJECTS)
                .description("List known projects in the database that have a Git repository attached.")
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    private ToolSpecification listGithubProjectsSpec() {
        return ToolSpecification.builder()
                .name(LIST_GITHUB_PROJECTS)
                .description("List known projects in the database that have GitHub repository metadata attached.")
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    private ToolSpecification listFilesInProjectSpec() {
        return ToolSpecification.builder()
                .name(LIST_PROJECT_ENTRIES)
                .description("List files and folders in a project's directory, optionally under a relative subpath.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory",
                                JsonStringSchema.builder().description("Absolute path to the project root directory")
                                        .build())
                        .addProperty("relativePath",
                                JsonStringSchema.builder().description("Optional sub-directory inside the project")
                                        .build())
                        .required("projectDirectory")
                        .build())
                .build();
    }

    private ToolSpecification getGitLogSpec() {
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

    private Map<String, Object> parseArgs(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (final Exception e) {
            return Map.of();
        }
    }

    private String listFolders(final Map<String, Object> args) {
        final Path path = resolvePathOrDefault(args, "path");
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

    private String listFolder(final Map<String, Object> args) {
        final Path path = resolvePathOrDefault(args, "path");
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

    private String explainPath(final Map<String, Object> args, final boolean detailed) {
        final Path target = resolvePathOrDefault(args, "path");
        if (!Files.exists(target)) {
            return "Path does not exist: " + target;
        }
        if (Files.isDirectory(target)) {
            return analyzeDirectory(target, detailed);
        }
        if (Files.isRegularFile(target)) {
            return analyzeFile(target, detailed);
        }
        return "Unsupported path type: " + target;
    }

    private String analyzeDirectory(final Path dir, final boolean detailed) {
        try (Stream<Path> stream = Files.walk(dir)) {
            final List<Path> all = stream.toList();
            final List<Path> files = all.stream().filter(Files::isRegularFile).toList();
            final List<Path> directories = all.stream().filter(Files::isDirectory).toList();

            final Map<String, Long> byExtension = files.stream()
                    .collect(Collectors.groupingBy(this::extensionOf, Collectors.counting()));
            final String extensionSummary = byExtension.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(detailed ? 10 : 5)
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));

            final List<String> sampleFiles = files.stream()
                    .map(path -> dir.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .limit(detailed ? 25 : 8)
                    .toList();

            final StringBuilder sb = new StringBuilder();
            sb.append(detailed ? "Detailed directory analysis\n" : "Directory summary\n");
            sb.append("path=").append(dir).append("\n");
            sb.append("directories=").append(Math.max(0, directories.size() - 1)).append("\n");
            sb.append("files=").append(files.size()).append("\n");
            sb.append("extensions=").append(extensionSummary.isBlank() ? "none" : extensionSummary).append("\n");
            if (!sampleFiles.isEmpty()) {
                sb.append(detailed ? "sampleFiles=\n" : "topFiles=\n");
                for (final String sample : sampleFiles) {
                    sb.append("- ").append(sample).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (final IOException e) {
            return "Failed to analyze directory " + dir + ": " + e.getMessage();
        }
    }

    private String analyzeFile(final Path file, final boolean detailed) {
        try {
            final long size = Files.size(file);
            final List<String> lines = Files.readAllLines(file);
            final String content = String.join("\n", lines);
            final int charCount = content.length();
            final int lineCount = lines.size();
            final long nonEmptyLines = lines.stream().filter(line -> !line.isBlank()).count();
            final long wordCount = Arrays.stream(content.split("\\s+"))
                    .filter(token -> !token.isBlank())
                    .count();

            final List<String> preview = lines.stream()
                    .filter(line -> !line.isBlank())
                    .limit(detailed ? 12 : 4)
                    .toList();

            final StringBuilder sb = new StringBuilder();
            sb.append(detailed ? "Detailed file analysis\n" : "File summary\n");
            sb.append("path=").append(file).append("\n");
            sb.append("extension=").append(extensionOf(file)).append("\n");
            sb.append("bytes=").append(size).append("\n");
            sb.append("lines=").append(lineCount).append("\n");
            sb.append("nonEmptyLines=").append(nonEmptyLines).append("\n");
            sb.append("words=").append(wordCount).append("\n");
            sb.append("characters=").append(charCount).append("\n");
            if (!preview.isEmpty()) {
                sb.append(detailed ? "contentPreview=\n" : "preview=\n");
                for (final String line : preview) {
                    sb.append("- ").append(trimToLength(line, detailed ? 180 : 120)).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (final IOException e) {
            return "Failed to analyze file " + file + ": " + e.getMessage();
        }
    }

    private String extensionOf(final Path path) {
        final String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        final int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "none";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String trimToLength(final String text, final int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private String getCwd() {
        return workingDirectoryService.getCurrentWorkingDirectory().toString();
    }

    private String changeWorkingDirectory(final Map<String, Object> args) {
        final Path newCwd = workingDirectoryService.navigateTo(require(args, "path"));
        return "cwd=" + newCwd;
    }

    private String resolvePath(final Map<String, Object> args) {
        final Path resolved = Path.of(require(args, "path")).toAbsolutePath().normalize();
        return resolved.toString();
    }

    private String pathInfo(final Map<String, Object> args) {
        final Path target = resolvePathOrDefault(args, "path");
        final boolean exists = Files.exists(target);
        final String type;
        if (!exists) {
            type = "missing";
        } else if (Files.isDirectory(target)) {
            type = "directory";
        } else if (Files.isRegularFile(target)) {
            type = "file";
        } else {
            type = "other";
        }

        return "path=" + target + "\n"
                + "exists=" + exists + "\n"
                + "type=" + type + "\n"
                + "readable=" + Files.isReadable(target) + "\n"
                + "writable=" + Files.isWritable(target);
    }

    private String listGitProjects() {
        final List<Project> projects = listGitProjectsFromDatabase();
        if (projects.isEmpty()) {
            final List<String> filesystemProjects = listGitProjectsFromFilesystem();
            if (filesystemProjects.isEmpty()) {
                return "No git projects found.";
            }
            return filesystemProjects.stream().map(p -> "- " + p).collect(Collectors.joining("\n"));
        }
        return projects.stream()
                .map(p -> "- " + p.getDirectory())
                .collect(Collectors.joining("\n"));
    }

    private String listGithubProjects() {
        final List<Project> projects = listGithubProjectsFromDatabase();
        if (projects.isEmpty()) {
            return "No GitHub projects found in database.";
        }
        return projects.stream()
                .map(p -> "- " + p.getDirectory() + " -> " + p.getGithubRepository().getName())
                .collect(Collectors.joining("\n"));
    }

    private List<Project> listGitProjectsFromDatabase() {
        if (Arc.container() == null || !Arc.container().isRunning()) {
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

    private List<Project> listGithubProjectsFromDatabase() {
        if (Arc.container() == null || !Arc.container().isRunning()) {
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

    private List<String> listGitProjectsFromFilesystem() {
        final Path cwd = workingDirectoryService.getCurrentWorkingDirectory();
        final int maxDepth = 4;
        try (Stream<Path> stream = Files.walk(cwd, maxDepth)) {
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

    private boolean isIgnoredDirectory(final Path path) {
        final String name = path.getFileName().toString();
        return name.equals(".git")
                || name.equals("node_modules")
                || name.equals("target")
                || name.equals("build")
                || name.equals(".idea")
                || name.equals(".vscode");
    }

    private String listFilesInProject(final Map<String, Object> args) {
        final Path project = Path.of(require(args, "projectDirectory")).normalize();
        final String relativePath = getString(args, "relativePath", "");
        final Path target = relativePath.isBlank() ? project : project.resolve(relativePath).normalize();
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
        final Path repoPath = resolvePathOrDefault(args, "path");
        final int maxCount = getInt(args, "maxCount", DEFAULT_GIT_LOG_COUNT);
        if (maxCount < 1 || maxCount > MAX_GIT_LOG_COUNT) {
            return "Invalid maxCount: expected a value between 1 and " + MAX_GIT_LOG_COUNT + ".";
        }

        final ProcessBuilder pb = new ProcessBuilder(
                "git",
                "-C",
                repoPath.toString(),
                "log",
                "--oneline",
                "-n",
                String.valueOf(maxCount));

        try {
            final Process process = pb.start();
            final String stdout = new String(process.getInputStream().readAllBytes()).trim();
            final String stderr = new String(process.getErrorStream().readAllBytes()).trim();
            final int exitCode = process.waitFor();

            if (exitCode != 0) {
                final String error = stderr.isBlank() ? "Unknown git error." : stderr;
                return "Failed to get git log for " + repoPath + ": " + error;
            }
            if (stdout.isBlank()) {
                return "No commits found in " + repoPath;
            }
            return "path=" + repoPath.toAbsolutePath().normalize() + "\n" + stdout;
        } catch (final Exception e) {
            return "Failed to get git log for " + repoPath + ": " + e.getMessage();
        }
    }

    private String require(final Map<String, Object> args, final String key) {
        final String val = getString(args, key, "");
        if (val.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return val;
    }

    private String getString(final Map<String, Object> args, final String key, final String defaultValue) {
        final Object val = args.get(key);
        return val == null ? defaultValue : String.valueOf(val);
    }

    private int getInt(final Map<String, Object> args, final String key, final int defaultValue) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof final Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private Path resolvePathOrDefault(final Map<String, Object> args, final String key) {
        final String raw = getString(args, key, "").trim();
        final Path base = defaultAnalysisRoot();
        if (raw.isBlank()) {
            return base;
        }
        final Path maybeRelative = Path.of(raw);
        return (maybeRelative.isAbsolute() ? maybeRelative : base.resolve(maybeRelative))
                .normalize()
                .toAbsolutePath();
    }

    private Path defaultAnalysisRoot() {
        return workingDirectoryService.getCurrentWorkingDirectory();
    }

    private static WorkingDirectoryService resolveWorkingDirectoryService() {
        if (Arc.container() != null && Arc.container().isRunning()) {
            final var instance = Arc.container().instance(WorkingDirectoryService.class);
            if (instance != null && instance.isAvailable()) {
                return instance.get();
            }
        }
        // Non-CDI fallback for plain unit tests.
        return new WorkingDirectoryService();
    }

}
