package ac.uk.sussex.kn253.resources.api;

import java.nio.file.Path;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class NoOriginsDefinedException extends WebApplicationException {

    private static final String MESSAGE_TEMPLATE = "Git repository at path '%s' has no configured fetch origins";

    public NoOriginsDefinedException(final Path path) {
        super(
                String.format(MESSAGE_TEMPLATE, path),
                Response.Status.NOT_FOUND);
    }

}
