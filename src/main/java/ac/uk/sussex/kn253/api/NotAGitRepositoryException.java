package ac.uk.sussex.kn253.api;

import java.nio.file.Path;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class NotAGitRepositoryException extends WebApplicationException {

    public NotAGitRepositoryException(final Path path, final String detail) {
        super(buildMessage(path, detail), Response.Status.BAD_REQUEST);
    }

    public NotAGitRepositoryException(final Path path) {
        this(path, "missing .git folder");
    }

    private static String buildMessage(final Path path, final String detail) {
        return String.format("Path '%s' is not a Git repository (%s)", path, detail);
    }

}
