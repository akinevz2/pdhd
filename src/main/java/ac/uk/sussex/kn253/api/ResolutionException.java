package ac.uk.sussex.kn253.api;

import java.nio.file.Path;

import org.jspecify.annotations.NonNull;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Exception thrown when a project resolution operation fails on the filesystem.
 *
 * <p>
 * Wraps lower-level I/O or process errors and includes context about the
 * filesystem path and what operation failed. Returns HTTP 500 Internal Server
 * Error.
 */
public class ResolutionException extends WebApplicationException {

    private final Path path;
    private final String failedOperation;

    /**
     * Constructs a ResolutionException with path, operation, and cause.
     *
     * @param path            the filesystem path where the resolution failed
     * @param failedOperation a string describing what failed to resolve
     * @param cause           the underlying exception
     */
    public ResolutionException(final @NonNull Path path, final @NonNull String failedOperation,
            final @NonNull Throwable cause) {
        super(
                String.format("Failed to resolve %s for %s: %s", failedOperation, path, cause.getMessage()),
                cause,
                Response.Status.INTERNAL_SERVER_ERROR);
        this.path = path;
        this.failedOperation = failedOperation;
    }

    /**
     * Returns the filesystem path where resolution failed.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns a description of what failed to be resolved.
     */
    public String getFailedOperation() {
        return failedOperation;
    }
}
