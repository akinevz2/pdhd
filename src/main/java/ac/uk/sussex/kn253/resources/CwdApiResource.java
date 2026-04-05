package ac.uk.sussex.kn253.resources;

import java.util.Map;

import ac.uk.sussex.kn253.services.CwdService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * REST endpoint backing the frontend cwd:get signal.
 */
@Path("/api/cwd")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CwdApiResource {

    public record CwdRequest(String path) {
    }

    @Inject
    CwdService cwdService;

    @GET
    public Map<String, String> currentWorkingDirectory() {
        final String cwd = cwdService.getCurrentWorkingDirectory();
        return Map.of("cwd", cwd);
    }

    @POST
    public Map<String, String> setCurrentWorkingDirectory(final CwdRequest request) {
        if (request == null) {
            throw new WebApplicationException("Request body is required", 400);
        }
        final String cwd = cwdService.setCurrentWorkingDirectory(request.path());
        return Map.of("cwd", cwd);
    }
}
