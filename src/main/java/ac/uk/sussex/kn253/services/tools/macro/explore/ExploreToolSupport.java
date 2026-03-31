package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.api.NotAGitRepositoryException;
import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.services.ProjectDiscoveryService;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import ac.uk.sussex.kn253.services.tools.*;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacros;
import ac.uk.sussex.kn253.services.tools.macro.read.ReadToolSupport;
import jakarta.enterprise.inject.Instance;

public class ExploreToolSupport {

    private static final Logger LOG = Logger.getLogger(ExploreToolSupport.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_GIT_LOG_COUNT = 20;
    private static final int MAX_GIT_LOG_COUNT = 200;
    private static final int GIT_SCAN_DEPTH = 4;
    private static final int DEFAULT_SEARCH_DEPTH = 4;
    private static final int MAX_SEARCH_DEPTH = 8;
    private static final int DEFAULT_SEARCH_LIMIT = 12;
    private static final int MAX_SEARCH_LIMIT = 50;

    public static final String SEARCH_TITLE = "Path search";

    public static final String KEY_ROOT = "root";
    public static final String KEY_QUERY = "query";
    public static final String KEY_DEPTH = "depth";
    public static final String KEY_FILES = "files";
    public static final String KEY_DIRS = "dirs";
    public static final String KEY_COUNT = "count";
    public static final String KEY_TOTAL = "total";
    public static final String KEY_TRUNCATED = "truncated";
    public static final String KEY_ITEMS = "items";
    public static final String KEY_NOTE = "note";
    public static final String KEY_NEXT = "next";
    public static final String KEY_TYPE = "type";
    public static final String KEY_MATCH = "match";
    public static final String KEY_REL = "rel";
    public static final String KEY_PATH = "path";

    public static final String VALUE_TYPE_DIR = "dir";
    public static final String VALUE_TYPE_FILE = "file";
    public static final String VALUE_NOTE_NO_MATCHES = "No matching paths found.";
    public static final String VALUE_NEXT_ASK_USER = "Prompt the user to choose when multiple paths look valid";

    public static final String ARG_PATH = "path";
    public static final String ARG_QUERY = "query";
    public static final String ARG_MAX_DEPTH = "maxDepth";
    public static final String ARG_LIMIT = "limit";
    public static final String ARG_INCLUDE_FILES = "includeFiles";
    public static final String ARG_INCLUDE_DIRECTORIES = "includeDirectories";
    public static final String ARG_PROJECT_DIRECTORY = "projectDirectory";
    public static final String ARG_RELATIVE_PATH = "relativePath";
    public static final String ARG_MAX_COUNT = "maxCount";

    public static final String OUTPUT_PREFIX_CWD = "cwd=";
    public static final String OUTPUT_PREFIX_PATH = "path=";
    public static final String OUTPUT_BULLET_PREFIX = "- ";

    public static final String MATCH_EXACT = "exact";
    public static final String MATCH_PREFIX = "prefix";
    public static final String MATCH_SUBSTRING = "substring";

    public static final String CMD_GIT = "git";
    public static final String CMD_GIT_LOG = "log";
    public static final String CMD_GIT_ONELINE = "--oneline";
    public static final String CMD_FLAG_COUNT = "-n";
    public static final String CMD_FLAG_CONTEXT = "-C";

    public static final String DIR_GIT = ".git";
    public static final String DIR_NODE_MODULES = "node_modules";
    public static final String DIR_TARGET = "target";
    public static final String DIR_BUILD = "build";
    public static final String DIR_IDEA = ".idea";
    public static final String DIR_VSCODE = ".vscode";

    private final WorkingDirectoryService workingDirectoryService;
    private final Instance<PathSummaryLlmService> pathSummaryLlmService;
    private final Instance<ProjectDiscoveryService> projectDiscoveryService;
    private final ReadToolSupport readToolSupport;

    // FIXME: TOO MANY NULLS
    public ExploreToolSupport(final WorkingDirectoryService workingDirectoryService) {
        this(workingDirectoryService, null, null);
    }

    public ExploreToolSupport(
            final WorkingDirectoryService workingDirectoryService,
            final Instance<PathSummaryLlmService> pathSummaryLlmService,
            final Instance<ProjectDiscoveryService> projectDiscoveryService) {
        this(workingDirectoryService, pathSummaryLlmService, projectDiscoveryService, new ReadToolSupport());
    }

    ExploreToolSupport(
            final WorkingDirectoryService workingDirectoryService,
            final Instance<PathSummaryLlmService> pathSummaryLlmService,
            final Instance<ProjectDiscoveryService> projectDiscoveryService,
            final ReadToolSupport readToolSupport) {
        this.workingDirectoryService = workingDirectoryService;
        this.pathSummaryLlmService = pathSummaryLlmService;
        this.projectDiscoveryService = projectDiscoveryService;
        this.readToolSupport = readToolSupport;
    }

    public String getCwd() {
        return workingDirectoryService.getCurrentWorkingDirectory().toString();
    }

    public String changeCwd(final Map<String, Object> args) {
        final Path newCwd = workingDirectoryService.navigateTo(ToolArguments.require(args, ARG_PATH));
        return OUTPUT_PREFIX_CWD + newCwd;
    }

    public String resolvePath(final Map<String, Object> args) {
        return resolveArg(args, ARG_PATH).toString();
    }

    public String searchPaths(final Map<String, Object> args) {
        final String query = ToolArguments.require(args, ARG_QUERY).trim();
        if (query.isBlank()) {
            return "Invalid query: expected a non-blank search term.";
        }

        final Path root = resolveArg(args, ARG_PATH);
        if (!Files.isDirectory(root)) {
            return "Not a directory: " + root;
        }

        final int requestedDepth = ToolArguments.getInt(args, ARG_MAX_DEPTH, DEFAULT_SEARCH_DEPTH);
        if (requestedDepth < 0 || requestedDepth > MAX_SEARCH_DEPTH) {
            return "Invalid maxDepth: expected a value between 0 and " + MAX_SEARCH_DEPTH + ".";
        }

        final int requestedLimit = ToolArguments.getInt(args, ARG_LIMIT, DEFAULT_SEARCH_LIMIT);
        if (requestedLimit < 1 || requestedLimit > MAX_SEARCH_LIMIT) {
            return "Invalid limit: expected a value between 1 and " + MAX_SEARCH_LIMIT + ".";
        }

        final boolean includeFiles = ToolArguments.getBoolean(args, ARG_INCLUDE_FILES, true);
        final boolean includeDirectories = ToolArguments.getBoolean(args, ARG_INCLUDE_DIRECTORIES, true);
        if (!includeFiles && !includeDirectories) {
            return "Invalid search options: at least one of includeFiles or includeDirectories must be true.";
        }

        final List<PathSearchMatch> matches = new ArrayList<>();
        final String queryLower = query.toLowerCase(Locale.ROOT);

        try {
            final Set<FileVisitOption> visitOptions = Set.of();
            Files.walkFileTree(root, visitOptions, requestedDepth,
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
        final Map<String, Object> response = new LinkedHashMap<>();
        response.put(KEY_ROOT, root.toAbsolutePath().normalize().toString());
        response.put(KEY_QUERY, query);
        response.put(KEY_DEPTH, requestedDepth);
        response.put(KEY_FILES, includeFiles);
        response.put(KEY_DIRS, includeDirectories);
        response.put(KEY_COUNT, limited.size());
        response.put(KEY_TOTAL, matches.size());
        response.put(KEY_TRUNCATED, matches.size() > limited.size());

        if (limited.isEmpty()) {
            response.put(KEY_ITEMS, List.of());
            response.put(KEY_NOTE, VALUE_NOTE_NO_MATCHES);
            return toSignalBlock(SEARCH_TITLE, response);
        }

        final List<Map<String, Object>> items = limited.stream()
                .map(match -> {
                    final Map<String, Object> item = new LinkedHashMap<>();
                    item.put(KEY_TYPE, match.directory() ? VALUE_TYPE_DIR : VALUE_TYPE_FILE);
                    item.put(KEY_MATCH, match.matchKind());
                    item.put(KEY_REL, match.relativePath());
                    item.put(KEY_PATH, match.absolutePath());
                    return item;
                })
                .toList();
        response.put(KEY_ITEMS, items);
        response.put(KEY_NEXT, VALUE_NEXT_ASK_USER);
        return toSignalBlock(SEARCH_TITLE, response);
    }

    private static String toSignalBlock(final String title, final Map<String, Object> payload) {
        try {
            return title + ":\n" + OBJECT_MAPPER.writeValueAsString(payload);
        } catch (final JsonProcessingException e) {
            return title + ":\n{}";
        }
    }

    public String pathInfo(final Map<String, Object> args) {
        return PathAnalyzer.pathInfo(resolveArg(args, ARG_PATH));
    }

    public String listSubdirectories(final Map<String, Object> args) {
        final Path path = resolveArg(args, ARG_PATH);
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
                return OUTPUT_PREFIX_PATH + path + "\nNo folders found in " + path;
            }
            return OUTPUT_PREFIX_PATH + path + "\n" + String.join("\n", folders);
        } catch (final IOException e) {
            return "Failed to list folders for " + path + ": " + e.getMessage();
        }
    }

    public String listFilesRecursive(final Map<String, Object> args) {
        final Path path = resolveArg(args, ARG_PATH);
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
                return OUTPUT_PREFIX_PATH + path + "\nNo files found in " + path;
            }
            return OUTPUT_PREFIX_PATH + path + "\n" + String.join("\n", files);
        } catch (final IOException e) {
            return "Failed to list files for " + path + ": " + e.getMessage();
        }
    }

    public String analyzePathDetailed(final Map<String, Object> args) {
        final Path path = resolveArg(args, ARG_PATH);
        final String result = PathAnalyzer.analyze(path, true);

        // Cache the analysis result (best effort, don't break on failure)
        try {
            final Path projectDir = readToolSupport.resolveProjectDirectory(path, null);
            readToolSupport.cachePathAnalysis(projectDir, path, result, true);
        } catch (final UnsupportedOperationException e) {
            LOG.debugf("Skipping path analysis cache: %s", e.getMessage());
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to cache path analysis for %s: %s", path, e.getMessage());
        }

        return result;
    }

    public String summarizePath(final Map<String, Object> args) {
        final Path path = resolveArg(args, ARG_PATH);
        final String result;

        if (pathSummaryLlmService != null && pathSummaryLlmService.isResolvable()) {
            String llmResult = null;
            try {
                llmResult = pathSummaryLlmService.get().summarizePath(path);
            } catch (final Exception ignored) {
                // fall through to static analysis
            }
            // The definition's signals map carries well-known output prefixes that
            // indicate an inability to summarise; fall back to the static analyser so
            // the tool always produces useful output (e.g. in tests without a live LLM).
            final Collection<String> summarySignals = ToolMacros.SUMMARIZE_PATH.signals().values();
            if (llmResult != null && summarySignals.stream().noneMatch(llmResult::startsWith)) {
                result = llmResult;
            } else {
                result = PathAnalyzer.analyze(path, false);
            }
        } else {
            result = PathAnalyzer.analyze(path, false);
        }

        // Cache the summary result (best effort, don't break on failure)
        try {
            final Path projectDir = readToolSupport.resolveProjectDirectory(path, null);
            readToolSupport.cachePathAnalysis(projectDir, path, result, false);
        } catch (final UnsupportedOperationException e) {
            LOG.debugf("Skipping path summary cache: %s", e.getMessage());
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to cache path summary for %s: %s", path, e.getMessage());
        }

        return result;
    }

    public String listGitProjects() {
        final Optional<String> discoveryError = discoverProjectsFromCwdSafely();
        if (discoveryError.isPresent()) {
            return discoveryError.get();
        }
        final List<Project> dbProjects = gitProjectsFromDb();
        if (!dbProjects.isEmpty()) {
            return dbProjects.stream()
                    .map(p -> OUTPUT_BULLET_PREFIX + p.getDirectory())
                    .collect(Collectors.joining("\n"));
        }
        final List<String> fsProjects = gitProjectsFromFilesystem();
        if (fsProjects.isEmpty()) {
            return "No git projects found.";
        }
        return fsProjects.stream().map(p -> OUTPUT_BULLET_PREFIX + p).collect(Collectors.joining("\n"));
    }

    public String listGithubProjects() {
        final Optional<String> discoveryError = discoverProjectsFromCwdSafely();
        if (discoveryError.isPresent()) {
            return discoveryError.get();
        }
        final List<Project> projects = githubProjectsFromDb();
        if (projects.isEmpty()) {
            return "No GitHub projects found in database.";
        }
        return projects.stream()
                .filter(p -> p.getGithubRepository() != null)
                .map(p -> OUTPUT_BULLET_PREFIX + p.getDirectory() + " -> " + p.getGithubRepository().getName())
                .collect(Collectors.joining("\n"));
    }

    public String listProjectEntries(final Map<String, Object> args) {
        final Path project = Path.of(ToolArguments.require(args, ARG_PROJECT_DIRECTORY)).normalize();
        final String relativePath = ToolArguments.getString(args, ARG_RELATIVE_PATH, "");
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

    public String getGitLog(final Map<String, Object> args) {
        final Path repoPath = resolveArg(args, ARG_PATH);
        final int maxCount = ToolArguments.getInt(args, ARG_MAX_COUNT, DEFAULT_GIT_LOG_COUNT);
        if (maxCount < 1 || maxCount > MAX_GIT_LOG_COUNT) {
            return "Invalid maxCount: expected a value between 1 and " + MAX_GIT_LOG_COUNT + ".";
        }
        try {
            final Process process = new ProcessBuilder(
                    CMD_GIT, CMD_FLAG_CONTEXT, repoPath.toString(), CMD_GIT_LOG, CMD_GIT_ONELINE, CMD_FLAG_COUNT,
                    String.valueOf(maxCount))
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
            return OUTPUT_PREFIX_PATH + repoPath.toAbsolutePath().normalize() + "\n" + stdout;
        } catch (final Exception e) {
            return "Failed to get git log for " + repoPath + ": " + e.getMessage();
        }
    }

    private Optional<String> discoverProjectsFromCwdSafely() {
        if (projectDiscoveryService == null || !projectDiscoveryService.isResolvable()) {
            return Optional.empty();
        }
        try {
            projectDiscoveryService.get().discoverFromCwd();
            return Optional.empty();
        } catch (final NotAGitRepositoryException e) {
            return Optional.of(e.getMessage());
        } catch (final Exception ignored) {
            return Optional.empty();
        }
    }

    private List<Project> gitProjectsFromDb() {
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

    public Path resolveArg(final Map<String, Object> args, final String key) {
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

    private static boolean isIgnoredDirectory(final Path path) {
        final String name = path.getFileName().toString();
        return name.equals(DIR_GIT)
                || name.equals(DIR_NODE_MODULES)
                || name.equals(DIR_TARGET)
                || name.equals(DIR_BUILD)
                || name.equals(DIR_IDEA)
                || name.equals(DIR_VSCODE);
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
            return MATCH_EXACT;
        }
        if (candidateNameLower.startsWith(queryLower)) {
            return MATCH_PREFIX;
        }
        if (candidateNameLower.contains(queryLower)) {
            return MATCH_SUBSTRING;
        }
        return null;
    }

    private static int matchRank(final String matchKind) {
        return switch (matchKind) {
            case MATCH_EXACT -> 0;
            case MATCH_PREFIX -> 1;
            default -> 2;
        };
    }

    private record PathSearchMatch(
            String absolutePath,
            String relativePath,
            boolean directory,
            String matchKind,
            int matchRank) {
    }
}