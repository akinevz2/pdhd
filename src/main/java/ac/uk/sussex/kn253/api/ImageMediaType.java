package ac.uk.sussex.kn253.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Utility class for detecting the MIME type of image files.
 *
 * <p>Detection first delegates to {@link Files#probeContentType(Path)}.  If
 * that returns a non-image type or fails, the file extension is used as a
 * fallback.  Only image media types are recognised; any other file type
 * results in a {@code null} return value.
 *
 * <p>This class is a stateless utility; all methods are static.
 */
public final class ImageMediaType {

    private ImageMediaType() {
        // utility class
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the image MIME type for {@code file}, or {@code null} when the
     * file is not a recognised image format.
     *
     * <p>Supported extensions (fallback): {@code .png}, {@code .jpg},
     * {@code .jpeg}, {@code .gif}, {@code .webp}, {@code .bmp}, {@code .svg}.
     *
     * @param file the path to inspect; must not be {@code null}.
     * @return an image MIME type string (e.g. {@code "image/png"}), or
     *         {@code null} if the file is not a recognised image.
     */
    public static String detect(final Path file) {
        // Try the OS/JVM content-type probe first.
        final String detected = probeContentType(file);
        if (detected != null) {
            return detected;
        }

        // Fall back to extension matching.
        return extensionMediaType(file.getFileName().toString().toLowerCase(Locale.ROOT));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String probeContentType(final Path file) {
        try {
            final String detected = Files.probeContentType(file);
            if (detected != null && detected.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return detected;
            }
        } catch (final IOException ignored) {
            // Fall through to extension checks.
        }
        return null;
    }

    private static String extensionMediaType(final String lowerName) {
        if (lowerName.endsWith(".png"))              return "image/png";
        if (lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg"))      return "image/jpeg";
        if (lowerName.endsWith(".gif"))              return "image/gif";
        if (lowerName.endsWith(".webp"))             return "image/webp";
        if (lowerName.endsWith(".bmp"))              return "image/bmp";
        if (lowerName.endsWith(".svg"))              return "image/svg+xml";
        return null;
    }
}
