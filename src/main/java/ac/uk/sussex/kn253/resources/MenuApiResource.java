package ac.uk.sussex.kn253.resources;

import java.util.Map;

import ac.uk.sussex.kn253.services.RuntimeManagementService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints that back the frontend's menu actions.
 */
@Path("/api/menu")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MenuApiResource {

    @Inject
    RuntimeManagementService runtimeManagementService;

    @POST
    @Path("/exit")
    public Response exitApplication() {
        runtimeManagementService.requestShutdown();
        return Response.accepted(Map.of("status", "shutting_down")).build();
    }
}
