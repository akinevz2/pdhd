package ac.uk.sussex.kn253.services;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Detects file types, mime types, and language associations based on file
 * paths and content inspection.
 */
@ApplicationScoped
public class FileTypeDetectionService {

    public String detectMimeType(final Path path) {
        try {
            final String detected = Files.probeContentType(path);
            if (detected != null && !detected.isBlank()) {
                return detected;
            }
        } catch (final Exception ignored) {
        }
        return "text/plain";
    }

    public boolean isPdfMimeType(final String mimeType, final Path path) {
        return "application/pdf".equalsIgnoreCase(mimeType)
                || path.getFileName().toString().toLowerCase().endsWith(".pdf");
    }

    public boolean isImageMimeType(final String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("image/");
    }

    public boolean isMarkdownPath(final Path path) {
        final String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".mdx");
    }

    public String detectLanguage(final Path path, final String mimeType) {
        final String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".ts") || name.endsWith(".tsx")) {
            return "typescript";
        }
        if (name.endsWith(".js") || name.endsWith(".jsx")) {
            return "javascript";
        }
        if (name.endsWith(".java")) {
            return "java";
        }
        if (isMarkdownPath(path)) {
            return "markdown";
        }
        if (mimeType != null && mimeType.toLowerCase().contains("json")) {
            return "json";
        }
        return "text";
    }

    public boolean isSummarisableFile(final Path file) {
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return false;
        }
        final String mimeType = detectMimeType(file);
        return !isPdfMimeType(mimeType, file) && !isImageMimeType(mimeType);
    }
}
