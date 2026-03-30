package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.services.ProjectDiscoveryService;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import ac.uk.sussex.kn253.services.tools.*;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacros;
import ac.uk.sussex.kn253.services.tools.macro.read.ReadToolSupport;
import jakarta.enterprise.inject.Instance;

public class ExploreToolSupport {

    private static final int DEFAULT_GIT_LOG_COUNT = 20;
    private static final int MAX_GIT_LOG_COUNT = 200;
    private static final int GIT_SCAN_DEPTH = 4;
    private static final int DEFAULT_SEARCH_DEPTH = 4;
    private static final int MAX_SEARCH_DEPTH = 8;
    private static final int DEFAULT_SEARCH_LIMIT = 12;
    private static final int MAX_SEARCH_LIMIT = 50;

    private final WorkingDirectoryService workingDirectoryService;
    private final Instance<PathSummaryLlmService> pathSummaryLlmService;
    private final Instance<ProjectDiscoveryService> projectDiscoveryService;
    private final ReadToolSupport readToolSupport;

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
        final Path newCwd = workingDirectoryService.navigateTo(ToolArguments.require(args, "path"));
        return "cwd=" + newCwd;
    }

    public String resolvePath(final Map<String, Object> args) {
        return resolveArg(args, "path").toString();
    }

    public String searchPaths(final Map<String, Object> args) {
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
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), requestedDepth,
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

    public String pathInfo(final Map<String, Object> args) {
        return PathAnalyzer.pathInfo(resolveArg(args, "path"));
    }

    public String listSubdirectories(final Map<String, Object> args) {
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

    public String listFilesRecursive(final Map<String, Object> args) {
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

    public String analyzePathDetailed(final Map<String, Object> args) {
        final Path path = resolveArg(args, "path");
        final String result = PathAnalyzer.analyze(path, true);

        // Cache the analysis result (best effort, don't break on failure)
        try {
            final Path projectDir = readToolSupport.resolveProjectDirectory(path, null);
            readToolSupport.cachePathAnalysis(projectDir, path, result, true);
        } catch (final Exception ignored) {
            // Caching failure should not break the tool
        }

        return result;
    }

    public String summarizePath(final Map<String, Object> args) {
        final Path path = resolveArg(args, "path");
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
        } catch (final Exception ignored) {
            // Caching failure should not break the tool
        }

        return result;
    }

    public String listGitProjects() {
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

    public String listGithubProjects() {
        discoverProjectsFromCwdSafely();
        final List<Project> projects = githubProjectsFromDb();
        if (projects.isEmpty()) {
            return "No GitHub projects found in database.";
        }
        return projects.stream()
                .map(p -> "- " + p.getDirectory() + " -> " + p.getGithubRepository().getName())
                .collect(Collectors.joining("\n"));
    }

    public String listProjectEntries(final Map<String, Object> args) {
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

    public String getGitLog(final Map<String, Object> args) {
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

    private void discoverProjectsFromCwdSafely() {
        if (projectDiscoveryService == null || !projectDiscoveryService.isResolvable()) {
            return;
        }
        try {
            projectDiscoveryService.get().discoverFromCwd();
        } catch (final Exception ignored) {
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

    private record PathSearchMatch(
            String absolutePath,
            String relativePath,
            boolean directory,
            String matchKind,
            int matchRank) {
    }
}