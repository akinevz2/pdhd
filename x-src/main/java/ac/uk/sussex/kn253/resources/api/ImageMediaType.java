package ac.uk.sussex.kn253.resources.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Utility class for detecting the MIME type of image files.
 *
 * <p>
 * Detection first delegates to {@link Files#probeContentType(Path)}. If
 * that returns a non-image type or fails, the file extension is used as a
 * fallback. Only image media types are recognised; any other file type
 * results in a {@code null} return value.
 *
 * <p>
 * This class is a stateless utility; all methods are static.
 */
public final class ImageMediaType {

    // FIXME: dynamic knowledge that can be stored in the database
    private static final String IMAGE_PREFIX = "image/";
    private static final String EXT_PNG = ".png";
    private static final String EXT_JPG = ".jpg";
    private static final String EXT_JPEG = ".jpeg";
    private static final String EXT_GIF = ".gif";
    private static final String EXT_WEBP = ".webp";
    private static final String EXT_BMP = ".bmp";
    private static final String EXT_SVG = ".svg";
    private static final String MEDIA_TYPE_PNG = "image/png";
    private static final String MEDIA_TYPE_JPEG = "image/jpeg";
    private static final String MEDIA_TYPE_GIF = "image/gif";
    private static final String MEDIA_TYPE_WEBP = "image/webp";
    private static final String MEDIA_TYPE_BMP = "image/bmp";
    private static final String MEDIA_TYPE_SVG = "image/svg+xml";

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
     * <p>
     * Supported extensions (fallback): {@code .png}, {@code .jpg},
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
            if (detected != null && detected.toLowerCase(Locale.ROOT).startsWith(IMAGE_PREFIX)) {
                return detected;
            }
        } catch (final IOException ignored) {
            // Fall through to extension checks.
        }
        return null;
    }

    private static String extensionMediaType(final String lowerName) {
        if (lowerName.endsWith(EXT_PNG)) {
            return MEDIA_TYPE_PNG;
        }
        if (lowerName.endsWith(EXT_JPG) || lowerName.endsWith(EXT_JPEG)) {
            return MEDIA_TYPE_JPEG;
        }
        if (lowerName.endsWith(EXT_GIF)) {
            return MEDIA_TYPE_GIF;
        }
        if (lowerName.endsWith(EXT_WEBP)) {
            return MEDIA_TYPE_WEBP;
        }
        if (lowerName.endsWith(EXT_BMP)) {
            return MEDIA_TYPE_BMP;
        }
        if (lowerName.endsWith(EXT_SVG)) {
            return MEDIA_TYPE_SVG;
        }
        return null;
    }
}
