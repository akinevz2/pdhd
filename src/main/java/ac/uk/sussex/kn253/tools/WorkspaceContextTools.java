package ac.uk.sussex.kn253.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ac.uk.sussex.kn253.services.CwdService;
import ac.uk.sussex.kn253.services.TelemetryService;
import ac.uk.sussex.kn253.support.ToolSupport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class WorkspaceContextTools {

    @Inject
    CwdService cwdService;

    @Inject
    TelemetryService telemetryService;

    @Tool("Get the backend current working directory as an absolute path.")
    public String getCurrentWorkingDirectory() {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        try {
            result = cwdService.getCurrentWorkingDirectory().toAbsolutePath().normalize().toString();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error getting working directory: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "getCurrentWorkingDirectory",
                        ToolSupport.MODULE_WORKSPACE,
                        "",
                        result,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        false);
            }
        }
    }

    @Tool("List the root directories of currently open projects.")
    @Transactional
    public List<String> getOpenProjectDirectories() {
        final long started = System.nanoTime();
        List<String> result = null;
        String errorClass = null;
        try {
            result = cwdService.getOpenProjectDirectories();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            return List.of("Error listing project directories: " + e.getMessage());
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "getOpenProjectDirectories",
                        ToolSupport.MODULE_WORKSPACE,
                        "",
                        result != null ? result.toString() : null,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        false);
            }
        }
    }

    @Tool(name = "listDirectoryContents", value = {
            "List direct files and folders from a directory path. If blank, use the current working directory."
    })
    public String listDirectoryContents(
            @P("Directory path to list. If blank, current working directory is used.") final String directoryPath) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path target = (directoryPath == null || directoryPath.isBlank())
                    ? cwdService.getCurrentWorkingDirectory().toAbsolutePath().normalize()
                    : Path.of(directoryPath).toAbsolutePath().normalize();

            if (!Files.exists(target)) {
                argumentValidationFailure = true;
                errorClass = IllegalArgumentException.class.getName();
                result = "Error listing directory: path does not exist: " + target;
                return result;
            }
            if (!Files.isDirectory(target)) {
                argumentValidationFailure = true;
                errorClass = IllegalArgumentException.class.getName();
                result = "Error listing directory: not a directory: " + target;
                return result;
            }

            try (var stream = Files.list(target)) {
                final List<String> entries = stream
                        .map(path -> (Files.isDirectory(path) ? "[D] " : "[F] ") + path.getFileName())
                        .sorted()
                        .collect(Collectors.toList());

                result = "Directory: " + target + "\n" + String.join("\n", entries);
                return result;
            }
        } catch (final IOException e) {
            errorClass = e.getClass().getName();
            result = "Error listing directory: " + e.getMessage();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error listing directory: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "listDirectoryContents",
                        ToolSupport.MODULE_WORKSPACE,
                        directoryPath,
                        result,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    @Tool(name = "change_working_directory", value = {
            "Change the backend working directory to a validated directory within known workspace roots."
    })
    public String changeWorkingDirectory(
            @P("Directory path to switch to.") final String directoryPath) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path newCwd = cwdService.changeWorkingDirectory(directoryPath);
            result = "Current working directory changed to: " + newCwd;
            return result;
        } catch (final IllegalArgumentException e) {
            argumentValidationFailure = true;
            errorClass = e.getClass().getName();
            result = "Error changing working directory: " + e.getMessage();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error changing working directory: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "change_working_directory",
                        ToolSupport.MODULE_WORKSPACE,
                        directoryPath,
                        result,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    @Tool(name = "list_files_recursive", value = {
            "Recursively list files under a directory with a maximum number of results."
    })
    public String listFilesRecursive(
            @P("Directory path to scan. If blank, current working directory is used.") final String directoryPath,
            @P("Maximum files to include in the result. Defaults to 200, min 1, max 1000.") final Integer maxResults) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path target = resolveDirectoryTarget(directoryPath);
            final int limit = normalizeLimit(maxResults);

            try (Stream<Path> stream = Files.walk(target)) {
                final List<String> entries = stream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(
                                path -> path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT)))
                        .limit(limit)
                        .map(path -> formatRelativePath(target, path))
                        .toList();

                result = "Directory: " + target + "\n"
                        + "Files listed: " + entries.size() + " (limit " + limit + ")\n"
                        + String.join("\n", entries);
                return result;
            }
        } catch (final IllegalArgumentException e) {
            argumentValidationFailure = true;
            errorClass = e.getClass().getName();
            result = "Error listing files recursively: " + e.getMessage();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error listing files recursively: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "list_files_recursive",
                        ToolSupport.MODULE_WORKSPACE,
                        directoryPath,
                        result,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    @Tool(name = "analyze_path_detailed", value = {
            "Return detailed metadata and a compact preview for a file or directory path."
    })
    public String analyzePathDetailed(
            @P("Path to inspect. If blank, current working directory is used.") final String path) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path target = resolvePathTarget(path);
            if (!Files.exists(target)) {
                throw new IllegalArgumentException("Path does not exist: " + target);
            }
            if (!isPathContained(target)) {
                throw new IllegalArgumentException("Path is outside allowed workspace roots: " + target);
            }

            final StringBuilder out = new StringBuilder();
            out.append("Path: ").append(target).append('\n');
            out.append("Type: ").append(Files.isDirectory(target) ? "directory" : "file").append('\n');
            out.append("Readable: ").append(Files.isReadable(target)).append('\n');
            out.append("Writable: ").append(Files.isWritable(target)).append('\n');
            out.append("Size bytes: ").append(Files.isDirectory(target) ? 0 : Files.size(target)).append('\n');
            final FileTime lastModified = Files.getLastModifiedTime(target);
            out.append("Last modified: ").append(lastModified).append('\n');

            if (Files.isDirectory(target)) {
                try (Stream<Path> stream = Files.list(target)) {
                    final List<String> children = stream
                            .sorted(Comparator
                                    .comparing(child -> child.getFileName().toString().toLowerCase(Locale.ROOT)))
                            .limit(30)
                            .map(child -> (Files.isDirectory(child) ? "[D] " : "[F] ") + child.getFileName())
                            .toList();
                    out.append("Children (max 30):").append('\n');
                    out.append(String.join("\n", children));
                }
            } else {
                final String mimeType = detectMimeType(target);
                out.append("MIME type: ").append(mimeType).append('\n');
                out.append("Preview:").append('\n');
                out.append(readPreview(target));
            }

            result = out.toString();
            return result;
        } catch (final IllegalArgumentException e) {
            argumentValidationFailure = true;
            errorClass = e.getClass().getName();
            result = "Error analyzing path: " + e.getMessage();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error analyzing path: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "analyze_path_detailed",
                        ToolSupport.MODULE_WORKSPACE,
                        path,
                        result,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    @Tool(name = "summarize_path", value = {
            "Summarize a file or directory quickly with lightweight counts and metadata."
    })
    public String summarizePath(
            @P("Path to summarize. If blank, current working directory is used.") final String path) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path target = resolvePathTarget(path);
            if (!Files.exists(target)) {
                throw new IllegalArgumentException("Path does not exist: " + target);
            }
            if (!isPathContained(target)) {
                throw new IllegalArgumentException("Path is outside allowed workspace roots: " + target);
            }

            if (Files.isDirectory(target)) {
                long fileCount;
                long directoryCount;
                try (Stream<Path> stream = Files.walk(target)) {
                    fileCount = stream.filter(Files::isRegularFile).count();
                }
                try (Stream<Path> stream = Files.walk(target)) {
                    directoryCount = stream.filter(Files::isDirectory).count();
                }

                result = "Directory summary\n"
                        + "Path: " + target + "\n"
                        + "Directories: " + directoryCount + "\n"
                        + "Files: " + fileCount;
                return result;
            }

            final String mimeType = detectMimeType(target);
            final long sizeBytes = Files.size(target);
            final String preview = readPreview(target);
            result = "File summary\n"
                    + "Path: " + target + "\n"
                    + "Size bytes: " + sizeBytes + "\n"
                    + "MIME type: " + mimeType + "\n"
                    + "Preview:\n" + preview;
            return result;
        } catch (final IllegalArgumentException e) {
            argumentValidationFailure = true;
            errorClass = e.getClass().getName();
            result = "Error summarizing path: " + e.getMessage();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error summarizing path: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "summarize_path",
                        ToolSupport.MODULE_WORKSPACE,
                        path,
                        result,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    private Path resolveDirectoryTarget(final String directoryPath) {
        final Path target = resolvePathTarget(directoryPath);
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("Path does not exist: " + target);
        }
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("Not a directory: " + target);
        }
        if (!cwdService.isFolderContained(target)) {
            throw new IllegalArgumentException("Directory is outside allowed workspace roots: " + target);
        }
        return target;
    }

    private Path resolvePathTarget(final String path) {
        return (path == null || path.isBlank())
                ? cwdService.getCurrentWorkingDirectory().toAbsolutePath().normalize()
                : Path.of(path).toAbsolutePath().normalize();
    }

    private boolean isPathContained(final Path target) {
        return cwdService.isFolderContained(target) || cwdService.isFileContained(target);
    }

    private int normalizeLimit(final Integer maxResults) {
        if (maxResults == null) {
            return 200;
        }
        if (maxResults < 1 || maxResults > 1000) {
            throw new IllegalArgumentException("maxResults must be between 1 and 1000");
        }
        return maxResults;
    }

    private String formatRelativePath(final Path root, final Path child) {
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path normalizedChild = child.toAbsolutePath().normalize();
        if (normalizedRoot.equals(normalizedChild)) {
            return ".";
        }
        return normalizedRoot.relativize(normalizedChild).toString().replace('\\', '/');
    }

    private String detectMimeType(final Path path) {
        try {
            final String detected = Files.probeContentType(path);
            if (detected != null && !detected.isBlank()) {
                return detected;
            }
        } catch (final Exception ignored) {
        }
        return "text/plain";
    }

    private String readPreview(final Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.limit(10).collect(Collectors.joining("\n"));
        } catch (final Exception ignored) {
            return "[preview unavailable]";
        }
    }
}
