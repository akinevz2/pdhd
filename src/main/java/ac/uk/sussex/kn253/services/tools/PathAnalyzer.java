package ac.uk.sussex.kn253.services.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for analysing files and directories.
 *
 * <p>
 * Provides both detailed and summary analysis of the file-system paths that
 * the AI assistant models ask about. All methods return human-readable strings
 * suitable for direct inclusion in tool-execution results.
 *
 * <p>
 * This class is not meant to be instantiated.
 */
public final class PathAnalyzer {

    /** Number of sample files to include in a concise directory summary. */
    private static final int SAMPLE_FILES_BRIEF = 8;

    /** Number of sample files to include in a detailed directory analysis. */
    private static final int SAMPLE_FILES_DETAILED = 25;

    /** Number of top file extensions to include in a concise summary. */
    private static final int TOP_EXTENSIONS_BRIEF = 5;

    /** Number of top file extensions to include in a detailed analysis. */
    private static final int TOP_EXTENSIONS_DETAILED = 10;

    /** Number of content preview lines to include in a brief file summary. */
    private static final int PREVIEW_LINES_BRIEF = 4;

    /** Number of content preview lines to include in a detailed file analysis. */
    private static final int PREVIEW_LINES_DETAILED = 12;

    /** Maximum line length (characters) for brief previews. */
    private static final int MAX_LINE_LENGTH_BRIEF = 120;

    /** Maximum line length (characters) for detailed previews. */
    private static final int MAX_LINE_LENGTH_DETAILED = 180;

    private PathAnalyzer() {
        // utility class – not instantiable
    }

    /**
     * Analyses a file or directory path, returning either a concise summary or a
     * detailed breakdown depending on {@code detailed}.
     *
     * @param target   the path to analyse; must exist.
     * @param detailed {@code true} for a detailed analysis, {@code false} for a
     *                 one-paragraph summary.
     * @return a human-readable analysis string, or an error message.
     */
    public static String analyze(final Path target, final boolean detailed) {
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

    /**
     * Returns basic metadata about a path: existence, type, readability, and
     * writability.
     *
     * @param target the path to inspect.
     * @return a newline-delimited key=value string.
     */
    public static String pathInfo(final Path target) {
        final boolean exists = Files.exists(target);
        final String type = resolveType(target, exists);

        return "path=" + target + "\n"
                + "exists=" + exists + "\n"
                + "type=" + type + "\n"
                + "readable=" + Files.isReadable(target) + "\n"
                + "writable=" + Files.isWritable(target);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String resolveType(final Path target, final boolean exists) {
        if (!exists) {
            return "missing";
        }
        if (Files.isDirectory(target)) {
            return "directory";
        }
        if (Files.isRegularFile(target)) {
            return "file";
        }
        return "other";
    }

    private static String analyzeDirectory(final Path dir, final boolean detailed) {
        try (Stream<Path> stream = Files.walk(dir)) {
            final List<Path> all = stream.toList();
            final List<Path> files = all.stream().filter(Files::isRegularFile).toList();
            final List<Path> directories = all.stream().filter(Files::isDirectory).toList();

            final String extensionSummary = buildExtensionSummary(files, detailed);
            final List<String> sampleFiles = buildSampleFileList(dir, files, detailed);

            final StringBuilder sb = new StringBuilder();
            sb.append(detailed ? "Detailed directory analysis\n" : "Directory summary\n");
            sb.append("path=").append(dir).append("\n");
            sb.append("directories=").append(Math.max(0, directories.size() - 1)).append("\n");
            sb.append("files=").append(files.size()).append("\n");
            sb.append("extensions=").append(extensionSummary.isBlank() ? "none" : extensionSummary).append("\n");
            appendSampleFiles(sb, sampleFiles, detailed);
            return sb.toString().trim();
        } catch (final IOException e) {
            return "Failed to analyze directory " + dir + ": " + e.getMessage();
        }
    }

    private static String buildExtensionSummary(final List<Path> files, final boolean detailed) {
        final Map<String, Long> byExtension = files.stream()
                .collect(Collectors.groupingBy(PathAnalyzer::extensionOf, Collectors.counting()));
        return byExtension.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(detailed ? TOP_EXTENSIONS_DETAILED : TOP_EXTENSIONS_BRIEF)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static List<String> buildSampleFileList(
            final Path dir, final List<Path> files, final boolean detailed) {
        return files.stream()
                .map(path -> dir.relativize(path).toString().replace('\\', '/'))
                .sorted()
                .limit(detailed ? SAMPLE_FILES_DETAILED : SAMPLE_FILES_BRIEF)
                .toList();
    }

    private static void appendSampleFiles(
            final StringBuilder sb, final List<String> sampleFiles, final boolean detailed) {
        if (sampleFiles.isEmpty()) {
            return;
        }
        sb.append(detailed ? "sampleFiles=\n" : "topFiles=\n");
        for (final String sample : sampleFiles) {
            sb.append("- ").append(sample).append("\n");
        }
    }

    private static String analyzeFile(final Path file, final boolean detailed) {
        try {
            final long size = Files.size(file);
            final List<String> lines = Files.readAllLines(file);
            final String content = String.join("\n", lines);
            final long nonEmptyLines = lines.stream().filter(line -> !line.isBlank()).count();
            final long wordCount = Arrays.stream(content.split("\\s+"))
                    .filter(token -> !token.isBlank())
                    .count();

            final List<String> preview = lines.stream()
                    .filter(line -> !line.isBlank())
                    .limit(detailed ? PREVIEW_LINES_DETAILED : PREVIEW_LINES_BRIEF)
                    .toList();

            final StringBuilder sb = new StringBuilder();
            sb.append(detailed ? "Detailed file analysis\n" : "File summary\n");
            sb.append("path=").append(file).append("\n");
            sb.append("extension=").append(extensionOf(file)).append("\n");
            sb.append("bytes=").append(size).append("\n");
            sb.append("lines=").append(lines.size()).append("\n");
            sb.append("nonEmptyLines=").append(nonEmptyLines).append("\n");
            sb.append("words=").append(wordCount).append("\n");
            sb.append("characters=").append(content.length()).append("\n");
            appendPreview(sb, preview, detailed);
            return sb.toString().trim();
        } catch (final IOException e) {
            return "Failed to analyze file " + file + ": " + e.getMessage();
        }
    }

    private static void appendPreview(
            final StringBuilder sb, final List<String> preview, final boolean detailed) {
        if (preview.isEmpty()) {
            return;
        }
        sb.append(detailed ? "contentPreview=\n" : "preview=\n");
        final int maxLen = detailed ? MAX_LINE_LENGTH_DETAILED : MAX_LINE_LENGTH_BRIEF;
        for (final String line : preview) {
            sb.append("- ").append(trimToLength(line, maxLen)).append("\n");
        }
    }

    /**
     * Returns the lowercase file extension for a path, or {@code "none"} when
     * the file has no extension or the extension is empty.
     *
     * @param path the path to inspect.
     * @return the extension string.
     */
    public static String extensionOf(final Path path) {
        final String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        final int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "none";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String trimToLength(final String text, final int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
