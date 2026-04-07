package ac.uk.sussex.kn253.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import ac.uk.sussex.kn253.events.CwdResolvedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Manages the backend working directory used by filesystem exploration APIs.
 */
@ApplicationScoped
public class CwdService {

    @Inject
    Event<CwdResolvedEvent> cwdResolvedEvents;

    private Path cwd;

    public CwdService() {
        this.cwd = Path.of("").toAbsolutePath().normalize();
    }

    public synchronized String getCurrentWorkingDirectory() {
        return cwd.toString();
    }

    /**
     * Updates the current working directory.
     *
     * <p>
     * Accepted forms:
     * <ul>
     * <li>absolute path</li>
     * <li>relative path (resolved against current cwd)</li>
     * <li>{@code ~} and {@code ~/...} (resolved against user home)</li>
     * </ul>
     *
     * @param path user-provided target directory
     * @return normalized absolute cwd after update
     */
    public synchronized String setCurrentWorkingDirectory(final String path) {
        final Path resolved = resolveDirectory(path);
        this.cwd = resolved;
        return resolved.toString();
    }

    /**
     * Resolves a directory path against the current working directory without
     * mutating the stored cwd.
     */
    public synchronized String resolveDirectoryPath(final String path) {
        return resolveDirectory(path).toString();
    }

    private Path resolveDirectory(final String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw badRequest("Path is required");
        }

        final String trimmed = rawPath.trim();
        final Path candidate;
        if ("~".equals(trimmed) || trimmed.startsWith("~/")) {
            final String home = System.getProperty("user.home");
            if (home == null || home.isBlank()) {
                throw badRequest("Unable to resolve home directory");
            }
            final String suffix = "~".equals(trimmed) ? "" : trimmed.substring(2);
            candidate = suffix.isBlank()
                    ? Path.of(home)
                    : Path.of(home).resolve(suffix);
        } else {
            final Path inputPath = Path.of(trimmed);
            candidate = inputPath.isAbsolute() ? inputPath : cwd.resolve(inputPath);
        }

        final Path normalized = candidate.normalize().toAbsolutePath();
        if (!Files.exists(normalized)) {
            throw badRequest("Directory does not exist: " + normalized);
        }
        if (!Files.isDirectory(normalized)) {
            throw badRequest("Path is not a directory: " + normalized);
        }
        if (cwdResolvedEvents != null) {
            cwdResolvedEvents.fire(new CwdResolvedEvent(rawPath, normalized.toString()));
        }
        return normalized;
    }

    private static WebApplicationException badRequest(final String message) {
        Objects.requireNonNull(message, "CwdService resolveDirectory subscription message is null");
        throw new WebApplicationException(message, Response.Status.BAD_REQUEST);
    }
}
