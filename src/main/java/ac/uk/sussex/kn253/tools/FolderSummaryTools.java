package ac.uk.sussex.kn253.tools;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import ac.uk.sussex.kn253.services.CwdService;
import ac.uk.sussex.kn253.services.TelemetryService;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Tools dedicated to folder summarisation workflows.
 */
@ApplicationScoped
public class FolderSummaryTools {

    private static final int MAX_TREE_LINES = 240;
    private static final int MAX_DEPTH = 3;
    private static final int MAX_LISTED_FILES = 240;
    private static final int MAX_FILE_READ_CHARS = 12000;

    @Inject
    CwdService cwdService;

    @Inject
    TelemetryService telemetryService;

    @Tool(name = "read_folder_manifest", value = {
            "Read a compact manifest for a folder path including immediate structure and technology hints."
    })
    public String readFolderManifest(final String folderPath) {
        final long started = System.nanoTime();
        String outputPayload = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            if (folderPath == null || folderPath.isBlank()) {
                argumentValidationFailure = true;
                throw new IllegalArgumentException("folderPath must not be blank");
            }

            final String resolvedPath = cwdService.resolveDirectoryPath(folderPath);
            final Path root = Path.of(resolvedPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                argumentValidationFailure = true;
                throw new IllegalArgumentException("Path is not a directory: " + root);
            }

            final String manifest = buildManifest(root);
            outputPayload = manifest;
            return manifest;
        } catch (final RuntimeException e) {
            errorClass = e.getClass().getName();
            outputPayload = "Error reading folder manifest: " + e.getMessage();
            return outputPayload;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            outputPayload = "Error reading folder manifest: " + e.getMessage();
            return outputPayload;
        } finally {
            if (telemetryService != null) {
                final long durationNanos = Math.max(0L, System.nanoTime() - started);
                telemetryService.recordToolUse(
                        "read_folder_manifest",
                        "FOLDER_SUMMARY",
                        "folderPath=" + folderPath,
                        outputPayload,
                        durationNanos,
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    @Tool(name = "list_folder_files", value = {
            "List relative files in a folder tree for targeted reading. Returns capped output ordered by path."
    })
    public String listFolderFiles(final String folderPath) {
        final long started = System.nanoTime();
        String outputPayload = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path root = resolveRoot(folderPath);
            final List<String> files = new ArrayList<>();
            try (var stream = Files.walk(root, MAX_DEPTH + 1)) {
                stream.filter(Files::isRegularFile)
                        .map(path -> root.relativize(path).toString().replace('\\', '/'))
                        .sorted()
                        .limit(MAX_LISTED_FILES)
                        .forEach(files::add);
            }

            final StringBuilder sb = new StringBuilder(4096);
            sb.append("folder: ").append(root).append('\n');
            sb.append("max_depth: ").append(MAX_DEPTH).append('\n');
            sb.append("files:\n");
            for (final String file : files) {
                sb.append("- ").append(file).append('\n');
            }
            if (files.size() >= MAX_LISTED_FILES) {
                sb.append("... file list truncated ...\n");
            }
            outputPayload = sb.toString();
            return outputPayload;
        } catch (final IllegalArgumentException e) {
            argumentValidationFailure = true;
            errorClass = e.getClass().getName();
            outputPayload = "Error listing folder files: " + e.getMessage();
            return outputPayload;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            outputPayload = "Error listing folder files: " + e.getMessage();
            return outputPayload;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "list_folder_files",
                        "FOLDER_SUMMARY",
                        "folderPath=" + folderPath,
                        outputPayload,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    @Tool(name = "read_folder_file", value = {
            "Read one file from a folder using a relative path. Returns text excerpt for summarisation evidence."
    })
    public String readFolderFile(final String folderPath, final String relativePath) {
        final long started = System.nanoTime();
        String outputPayload = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path root = resolveRoot(folderPath);
            if (relativePath == null || relativePath.isBlank()) {
                argumentValidationFailure = true;
                throw new IllegalArgumentException("relativePath must not be blank");
            }

            final Path candidate = root.resolve(relativePath).normalize();
            if (!candidate.startsWith(root)) {
                argumentValidationFailure = true;
                throw new IllegalArgumentException("relativePath escapes root folder");
            }
            if (!Files.isRegularFile(candidate)) {
                argumentValidationFailure = true;
                throw new IllegalArgumentException("Not a file: " + relativePath);
            }

            final String content = Files.readString(candidate);
            final String clipped = content.length() > MAX_FILE_READ_CHARS
                    ? content.substring(0, MAX_FILE_READ_CHARS) + "\n... file content truncated ..."
                    : content;

            final StringBuilder sb = new StringBuilder(Math.min(16384, clipped.length() + 256));
            sb.append("file: ").append(root.relativize(candidate).toString().replace('\\', '/')).append('\n');
            sb.append("chars: ").append(content.length()).append('\n');
            sb.append("content:\n");
            sb.append(clipped);
            outputPayload = sb.toString();
            return outputPayload;
        } catch (final IllegalArgumentException e) {
            argumentValidationFailure = true;
            errorClass = e.getClass().getName();
            outputPayload = "Error reading folder file: " + e.getMessage();
            return outputPayload;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            outputPayload = "Error reading folder file: " + e.getMessage();
            return outputPayload;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "read_folder_file",
                        "FOLDER_SUMMARY",
                        "folderPath=" + folderPath + ", relativePath=" + relativePath,
                        outputPayload,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    private Path resolveRoot(final String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalArgumentException("folderPath must not be blank");
        }
        final String resolvedPath = cwdService.resolveDirectoryPath(folderPath);
        final Path root = Path.of(resolvedPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Path is not a directory: " + root);
        }
        return root;
    }

    private String buildManifest(final Path root) throws IOException {
        final List<String> treeLines = new ArrayList<>();
        final Map<String, Integer> extensionCounts = new HashMap<>();
        final long[] fileCount = { 0L };
        final long[] directoryCount = { 0L };

        Files.walkFileTree(root, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                final int depth = root.relativize(dir).getNameCount();
                if (depth > MAX_DEPTH) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                directoryCount[0]++;
                if (treeLines.size() < MAX_TREE_LINES) {
                    final String prefix = depth == 0 ? "" : "  ".repeat(depth) + "- ";
                    final String name = depth == 0
                            ? dir.getFileName() == null ? dir.toString() : dir.getFileName().toString()
                            : dir.getFileName().toString();
                    treeLines.add(prefix + name + "/");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                final int depth = root.relativize(file).getNameCount();
                fileCount[0]++;

                final String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                final int extStart = fileName.lastIndexOf('.');
                final String extension = extStart >= 0 && extStart < fileName.length() - 1
                        ? fileName.substring(extStart + 1).toLowerCase()
                        : "(noext)";
                extensionCounts.merge(extension, 1, Integer::sum);

                if (depth <= MAX_DEPTH && treeLines.size() < MAX_TREE_LINES) {
                    final String prefix = "  ".repeat(depth) + "- ";
                    treeLines.add(prefix + fileName);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        final List<Map.Entry<String, Integer>> topExtensions = extensionCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(12)
                .toList();

        final StringBuilder sb = new StringBuilder(4096);
        sb.append("folder: ").append(root).append('\n');
        sb.append("directories: ").append(Math.max(0L, directoryCount[0] - 1L)).append('\n');
        sb.append("files: ").append(fileCount[0]).append('\n');
        sb.append("scan_depth: ").append(MAX_DEPTH).append('\n');
        sb.append("\n");
        sb.append("tree:\n");
        for (final String line : treeLines) {
            sb.append(line).append('\n');
        }
        if (treeLines.size() >= MAX_TREE_LINES) {
            sb.append("... tree output truncated ...\n");
        }

        sb.append("\n");
        sb.append("top_extensions:\n");
        for (final Map.Entry<String, Integer> entry : topExtensions) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }

        return sb.toString();
    }
}
