package ac.uk.sussex.kn253.api;

import java.nio.file.Path;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class NotAGithubRepositoryException extends WebApplicationException {

    public static final String GITHUB_CLI_MISSING = "GitHub CLI is not installed or not on PATH";

    public NotAGithubRepositoryException(final Path path, final String detail) {
        super(buildMessage(path, detail), Response.Status.NOT_FOUND);
    }

    public NotAGithubRepositoryException(final Path path) {
        this(path, "repository is not linked to GitHub");
    }

    private static String buildMessage(final Path path, final String detail) {
        return String.format("Path '%s' cannot be resolved to a GitHub repository (%s)", path, detail);
    }
}
