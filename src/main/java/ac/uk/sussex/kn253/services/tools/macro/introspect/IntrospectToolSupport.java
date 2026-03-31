package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.model.ProjectKnowledge;
import ac.uk.sussex.kn253.services.*;
import ac.uk.sussex.kn253.services.ToolActivityService.ToolActivityEvent;
import ac.uk.sussex.kn253.services.ToolTelemetryService.ToolTelemetrySnapshot;
import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.read.ReadToolSupport;

public class IntrospectToolSupport {

    private static final Logger LOG = Logger.getLogger(IntrospectToolSupport.class);

    private static final int MAX_FOLDER_PATHS = 600;
    private static final int MAX_FOLDER_FILES_WITH_CONTENT = 24;
    private static final int MAX_SOURCE_PATHS = 400;
    private static final int MAX_SOURCE_FILES_WITH_CONTENT = 40;
    private static final int RECENT_TOOL_CALLS = 12;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, Long> CACHE_TTL_SECONDS = Map.of(
            "file_content", 600L,
            "path_analysis", 300L,
            "folder_manifest", 300L);

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
    private final ToolTelemetryService toolTelemetryService;
    private final ReadToolSupport readToolSupport;
    private final FolderManifestShaper folderManifestShaper;

    // FIXME: there are too many nulls here
    public IntrospectToolSupport(final WorkingDirectoryService workingDirectoryService) {
        this(workingDirectoryService, null, null, new ReadToolSupport(), new DefaultFolderManifestShaper());
    }

    public IntrospectToolSupport(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService) {
        this(workingDirectoryService, toolActivityService, null, new ReadToolSupport(),
                new DefaultFolderManifestShaper());
    }

    public IntrospectToolSupport(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService,
            final ToolTelemetryService toolTelemetryService) {
        this(workingDirectoryService, toolActivityService, toolTelemetryService, new ReadToolSupport(),
                new DefaultFolderManifestShaper());
    }

    public IntrospectToolSupport(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService,
            final ToolTelemetryService toolTelemetryService,
            final FolderManifestShaper folderManifestShaper) {
        this(workingDirectoryService, toolActivityService, toolTelemetryService, new ReadToolSupport(),
                folderManifestShaper);
    }

    IntrospectToolSupport(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService,
            final ToolTelemetryService toolTelemetryService,
            final ReadToolSupport readToolSupport,
            final FolderManifestShaper folderManifestShaper) {
        this.workingDirectoryService = workingDirectoryService;
        this.toolActivityService = toolActivityService;
        this.toolTelemetryService = toolTelemetryService;
        this.readToolSupport = readToolSupport;
        this.folderManifestShaper = folderManifestShaper;
    }

    /**
     * Tool-contract adapter. ToolMacro executes with a generic argument map, but
     * this support class prefers a typed path entrypoint.
     */
    public String readFolderManifest(final Map<String, Object> args) {
        return readFolderManifest(ToolArguments.getString(args, "path", ""));
    }

    /**
     * Builds an evidence-based manifest for a folder path.
     *
     * @param rawPath absolute path, or path relative to cwd; blank means cwd
     * @return formatted manifest output suitable for assistant consumption
     */
    public String readFolderManifest(final String rawPath) {
        // FIXME: this method is doing too much: path resolution, error handling,
        // manifest shaping, caching. Refactor to smaller methods with clearer
        // responsibilities.
        final Path dir = Path.of(rawPath);
        if (dir == null) {
            return "Not a directory: " + rawPath;
        }
        if (ProjectRootSupport.isProjectRootDirectory(dir)) {
            return ManifestPromptSupport.READ_FOLDER_MANIFEST_NOT_FOR_ROOT + " path=" + dir;
        }

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

        final List<Path> files = allEntries.stream().filter(Files::isRegularFile).toList();
        final List<Path> sampledFiles = sampleFolderFilesForContent(files);
        final String result = folderManifestShaper.shapeFolderManifest(new FolderManifestShapeInput(
                dir,
                allEntries,
                files,
                readSampledFileContents(sampledFiles),
                manifestShapePolicy()));

        // Cache the folder manifest result (best effort, don't break on failure)
        try {
            final Path projectDir = readToolSupport.resolveProjectDirectory(dir, null);
            readToolSupport.cacheFolderManifest(projectDir, dir, result);
        } catch (final UnsupportedOperationException e) {
            LOG.debugf("Skipping folder manifest cache: %s", e.getMessage());
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to cache folder manifest for %s: %s", dir, e.getMessage());
        }

        return result;
    }

    public String readProjectManifest(final Map<String, Object> args) {
        final Path dir = resolveDirectoryArg(args, "path");
        if (dir == null) {
            return "Not a directory: " + ToolArguments.getString(args, "path", "");
        }
        if (!ProjectRootSupport.isProjectRootDirectory(dir)) {
            return ManifestPromptSupport.READ_PROJECT_MANIFEST_ONLY_FOR_ROOTS + " path=" + dir;
        }

        final Map<String, String> manifestContents = new LinkedHashMap<>();
        for (final String filename : MANIFEST_FILENAMES) {
            final Path file = dir.resolve(filename);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                manifestContents.put(filename, Files.readString(file, StandardCharsets.UTF_8));
            } catch (final IOException e) {
                manifestContents.put(filename, "(unreadable: " + e.getMessage() + ")");
            }
        }

        final SourceSummary sourceSummary = collectSourceSummary(dir);
        return folderManifestShaper.shapeProjectManifest(new ProjectManifestShapeInput(
                dir,
                manifestContents,
                sourceSummary.sourceRoot(),
                sourceSummary.sourceFiles(),
                sourceSummary.sampledSourceContents(),
                sourceSummary.scanError(),
                manifestShapePolicy()));
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
                    + cacheFreshnessSummary(knowledge) + "\n"
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
                    .append(" ")
                    .append(cacheFreshnessSummary(entry))
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

        if (toolTelemetryService != null) {
            final List<ToolTelemetrySnapshot> snapshots = toolTelemetryService.snapshot();
            if (snapshots.isEmpty()) {
                sb.append("\nNo telemetry recorded this session.\n");
            } else {
                sb.append("\nTool telemetry summary:\n");
                for (final ToolTelemetrySnapshot snapshot : snapshots) {
                    sb.append("- ")
                            .append(snapshot.toolName())
                            .append(" module=")
                            .append(snapshot.moduleName())
                            .append(" calls=")
                            .append(snapshot.invocations())
                            .append(" failures=")
                            .append(snapshot.failures())
                            .append(" validationFailures=")
                            .append(snapshot.argumentValidationFailures())
                            .append(" avgMs=")
                            .append(formatMs(snapshot.averageDurationMs()))
                            .append(" p50Ms=")
                            .append(formatMs(snapshot.p50DurationMs()))
                            .append(" p95Ms=")
                            .append(formatMs(snapshot.p95DurationMs()));
                    if (!snapshot.errorClasses().isEmpty()) {
                        sb.append(" errorClasses=").append(snapshot.errorClasses());
                    }
                    sb.append("\n");
                }
            }
        } else {
            sb.append("\n(Tool telemetry not available)\n");
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

    private SourceSummary collectSourceSummary(final Path dir) {
        final Path srcDir = dir.resolve("src");
        if (!Files.isDirectory(srcDir)) {
            return new SourceSummary(null, List.of(), Map.of(), null);
        }

        final List<Path> sourceFiles;
        try (Stream<Path> stream = Files.walk(srcDir)) {
            sourceFiles = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> srcDir.relativize(path).toString()))
                    .limit(MAX_SOURCE_PATHS)
                    .toList();
        } catch (final IOException e) {
            return new SourceSummary(srcDir, List.of(), Map.of(), e.getMessage());
        }

        final List<Path> sampledFiles = sampleSourceFilesForContent(sourceFiles);
        return new SourceSummary(srcDir, sourceFiles, readSampledFileContents(sampledFiles), null);
    }

    private Map<Path, String> readSampledFileContents(final List<Path> sampledFiles) {
        final Map<Path, String> sampledContents = new LinkedHashMap<>();
        for (final Path file : sampledFiles) {
            try {
                sampledContents.put(file, Files.readString(file, StandardCharsets.UTF_8));
            } catch (final IOException e) {
                sampledContents.put(file, "(unreadable: " + e.getMessage() + ")");
            }
        }
        return sampledContents;
    }

    private ManifestShapePolicy manifestShapePolicy() {
        return new ManifestShapePolicy(
                MAX_FOLDER_PATHS,
                MAX_FOLDER_FILES_WITH_CONTENT,
                MAX_SOURCE_PATHS,
                MAX_SOURCE_FILES_WITH_CONTENT);
    }

    private record SourceSummary(
            Path sourceRoot,
            List<Path> sourceFiles,
            Map<Path, String> sampledSourceContents,
            String scanError) {
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

    private String cacheFreshnessSummary(final ProjectKnowledge knowledge) {
        try {
            final JsonNode root = OBJECT_MAPPER.readTree(knowledge.getJsonContent());
            final String type = root.path("type").asText("").trim();
            if (type.isBlank() || !CACHE_TTL_SECONDS.containsKey(type)) {
                return "cacheStatus=not_applicable";
            }

            final long ttlSeconds = CACHE_TTL_SECONDS.get(type);
            final String cachedAtRaw = root.path("cachedAt").asText("").trim();
            final java.time.Instant cachedAt = cachedAtRaw.isBlank()
                    ? knowledge.getUpdatedAt()
                    : java.time.Instant.parse(cachedAtRaw);
            final long ageSeconds = Math.max(0L,
                    java.time.Duration.between(cachedAt, java.time.Instant.now()).getSeconds());
            final boolean stale = ageSeconds > ttlSeconds;

            return "cacheStatus=" + (stale ? "stale" : "fresh")
                    + " cacheType=" + type
                    + " ageSeconds=" + ageSeconds
                    + " ttlSeconds=" + ttlSeconds;
        } catch (final Exception e) {
            return "cacheStatus=unknown";
        }
    }

    private String formatMs(final double millis) {
        return String.format(Locale.ROOT, "%.2f", millis);
    }

}