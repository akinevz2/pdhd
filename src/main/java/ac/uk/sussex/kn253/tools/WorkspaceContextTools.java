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

    @Tool(name = "getCurrentWorkingDirectory", value = {
            "Returns the server-side current working directory as an absolute filesystem path string (e.g. '/home/user/projects/myapp').",
            " Call this tool when the user asks about the current directory, working directory, or base path.",
            " Do NOT use this tool to list directory contents — use listDirectoryContents instead.",
            " On success, returns the absolute path as a plain string.",
            " On failure, returns a string starting with 'Error getting working directory:'." })
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

    @Tool(name = "getOpenProjectDirectories", value = {
            "Returns a list of absolute path strings for all currently open/loaded project root directories.",
            " Call this tool when the user asks which projects are open, available, or loaded.",
            " Do NOT use this to get the current working directory — use getCurrentWorkingDirectory for that.",
            " On success, returns a list of absolute path strings (may be empty if no projects are loaded).",
            " On failure, returns a single-element list containing a string starting with 'Error listing project directories:'." })
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
            "Lists the immediate (non-recursive) children of a directory, showing files as '[F] name' and subdirectories as '[D] name'.",
            " Call this tool to browse top-level directory contents.",
            " Use list_files_recursive instead when you need to search all nested files.",
            " On success, returns a string starting with 'Directory: <path>' followed by sorted entries.",
            " On failure, returns a string starting with 'Error listing directory:'." })
    public String listDirectoryContents(
            @P("Absolute or relative directory path to list. If blank or omitted, the current working directory is used. Relative paths are resolved from the current working directory.") final String directoryPath) {
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
            "Changes the server-side working directory to the given path, which must be inside a known open project root.",
            " Call this tool when the user explicitly asks to change, switch, or navigate to a different directory.",
            " Does NOT list or read directory contents — use listDirectoryContents or readFile for that.",
            " On success, returns a string starting with 'Current working directory changed to: <path>'.",
            " On failure, returns a string starting with 'Error changing working directory:'." })
    public String changeWorkingDirectory(
            @P("Absolute or relative path of the directory to switch to. Must resolve to a directory within an open project root. Required — must not be blank.") final String directoryPath) {
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
            "Recursively lists all regular files under a directory tree, sorted by path, up to a configurable limit.",
            " Call this tool to discover nested files or explore a project's full file tree.",
            " Use listDirectoryContents instead when you only need top-level directory entries.",
            " On success, returns a string starting with 'Directory: <path>\\nFiles listed: <count> (limit <n>)' followed by relative file paths.",
            " On failure, returns a string starting with 'Error listing files recursively:'." })
    public String listFilesRecursive(
            @P("Absolute or relative directory path to scan. If blank or omitted, the current working directory is used. Relative paths are resolved from the current working directory.") final String directoryPath,
            @P("Maximum number of files to include. Integer between 1 and 1000 inclusive. If omitted, defaults to 200.") final Integer maxResults) {
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
            "Returns detailed metadata for a file or directory: type, readable/writable flags, size, last-modified time, MIME type (for files), and either a content preview or a child listing (up to 30 entries for directories).",
            " Call this tool when you need to inspect file metadata or get a quick preview without reading the full file.",
            " Use readFile to retrieve complete file contents. Use summarize_path for a lighter-weight count-only summary.",
            " On success, returns a multi-line string starting with 'Path: <absolute-path>'.",
            " On failure, returns a string starting with 'Error analyzing path:'." })
    public String analyzePathDetailed(
            @P("Absolute or relative path of the file or directory to inspect. If blank or omitted, the current working directory is used. Must be within an open project root.") final String path) {
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
            "Returns a lightweight summary of a file or directory: for directories, total file and subdirectory counts; for files, size, MIME type, and a short content preview.",
            " Call this tool for a quick structural overview without reading the full file.",
            " Use readFile for complete file contents. Use analyze_path_detailed for full metadata including last-modified time.",
            " On success, returns a string starting with 'Directory summary' or 'File summary'.",
            " On failure, returns a string starting with 'Error summarizing path:'." })
    public String summarizePath(
            @P("Absolute or relative path to summarize. If blank or omitted, the current working directory is used. Must be within an open project root.") final String path) {
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
