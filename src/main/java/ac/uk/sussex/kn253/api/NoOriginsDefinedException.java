package ac.uk.sussex.kn253.api;

import java.nio.file.Path;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class NoOriginsDefinedException extends WebApplicationException {

    public NoOriginsDefinedException(final Path path) {
        super(
                String.format("Git repository at path '%s' has no configured fetch origins", path),
                Response.Status.NOT_FOUND);
    }

}
