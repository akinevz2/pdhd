package ac.uk.sussex.kn253.resources;

import java.util.List;
import java.util.Map;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints backing project:* frontend signals.
 */
@Path("/api/project")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectApiResource {

    public record ProjectSummaryResponse(long id, String directory, boolean hasGitRepository, boolean loaded) {
    }

    public record ProjectStateRequest(long id) {
    }

    @GET
    @Path("/list")
    public List<ProjectSummaryResponse> listProjects() {
        return ProjectFolder.<ProjectFolder>listAll().stream()
                .map(project -> new ProjectSummaryResponse(
                        project.id == null ? -1L : project.id,
                        project.getDirectory(),
                        project.getGitRepository() != null,
                        project.isLoaded()))
                .toList();
    }

    @POST
    @Path("/load")
    @Transactional
    public Map<String, String> markProjectLoaded(final ProjectStateRequest request) {
        final ProjectFolder project = findProjectOrThrow(request);
        project.setLoaded(true);
        return Map.of("status", "loaded");
    }

    @POST
    @Path("/unload")
    @Transactional
    public Map<String, String> markProjectUnloaded(final ProjectStateRequest request) {
        final ProjectFolder project = findProjectOrThrow(request);
        project.setLoaded(false);
        return Map.of("status", "unloaded");
    }

    private ProjectFolder findProjectOrThrow(final ProjectStateRequest request) {
        if (request == null || request.id() <= 0) {
            throw new WebApplicationException("Project id is required", Response.Status.BAD_REQUEST);
        }
        final ProjectFolder project = ProjectFolder.findById(request.id());
        if (project == null) {
            throw new WebApplicationException("Project not found: " + request.id(), Response.Status.NOT_FOUND);
        }
        return project;
    }
}
