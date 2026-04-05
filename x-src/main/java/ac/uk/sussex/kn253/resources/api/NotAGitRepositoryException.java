package ac.uk.sussex.kn253.resources.api;

import java.nio.file.Path;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class NotAGitRepositoryException extends WebApplicationException {

    private static final String DEFAULT_DETAIL = "missing .git folder";
    private static final String MESSAGE_TEMPLATE = "Path '%s' is not a Git repository (%s)";

    public NotAGitRepositoryException(final Path path, final String detail) {
        super(buildMessage(path, detail), Response.Status.BAD_REQUEST);
    }

    public NotAGitRepositoryException(final Path path) {
        this(path, DEFAULT_DETAIL);
    }

    private static String buildMessage(final Path path, final String detail) {
        return String.format(MESSAGE_TEMPLATE, path, detail);
    }

}
