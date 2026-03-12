package ac.uk.sussex.kn253.ollama;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import ac.uk.sussex.kn253.ollama.model.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * MicroProfile REST Client for the Ollama management API.
 *
 * <p>
 * The base URL is configured via {@code ollama.base-url} in
 * {@code application.properties}.
 * Register this client in your properties:
 *
 * <pre>
 * quarkus.rest-client.ollama-management.url=${ollama.base-url}
 * </pre>
 *
 * <p>
 * Inject it with:
 *
 * <pre>
 * {@literal @}RestClient
 * OllamaManagementClient client;
 * </pre>
 *
 * <p>
 * All endpoints mirror the
 * <a href="https://github.com/ollama/ollama/blob/main/docs/api.md">
 * Ollama REST API</a>.
 */
@RegisterRestClient(configKey = "ollama-management")
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public interface OllamaManagementClient {

    // -------------------------------------------------------------------------
    // Health / version
    // -------------------------------------------------------------------------

    /**
     * Checks whether the Ollama server is reachable.
     * A healthy server returns HTTP 200 with body {@code "Ollama is running"}.
     *
     * @return raw JAX-RS {@link Response} so callers can inspect the status code.
     */
    @GET
    @Path("/")
    @Produces(MediaType.TEXT_PLAIN)
    Response health();

    /**
     * Returns the Ollama server version as a plain-text string.
     */
    @GET
    @Path("/version")
    @Produces(MediaType.APPLICATION_JSON)
    String version();

    // -------------------------------------------------------------------------
    // Model listing
    // -------------------------------------------------------------------------

    /**
     * Lists all models that are locally available on the Ollama server.
     *
     * @return {@link OllamaTagsResponse} containing the list of models.
     */
    @GET
    @Path("/tags")
    OllamaTagsResponse listModels();

    /**
     * Lists models that are currently loaded into memory (GPU/CPU).
     *
     * @return {@link OllamaPsResponse} containing running models.
     */
    @GET
    @Path("/ps")
    OllamaPsResponse listRunningModels();

    // -------------------------------------------------------------------------
    // Model inspection
    // -------------------------------------------------------------------------

    /**
     * Returns detailed information about a specific model.
     *
     * @param request body containing the model name.
     * @return {@link OllamaShowResponse} with model metadata.
     */
    @POST
    @Path("/show")
    OllamaShowResponse showModel(OllamaShowRequest request);

    // -------------------------------------------------------------------------
    // Model lifecycle
    // -------------------------------------------------------------------------

    /**
     * Pulls (downloads) a model from the Ollama registry.
     *
     * <p>
     * Set {@code stream = false} in the request to block until the pull is
     * complete and receive a single {@link OllamaPullStatus} response.
     *
     * @param request body specifying the model name and streaming preference.
     * @return final pull status.
     */
    @POST
    @Path("/pull")
    OllamaPullStatus pullModel(OllamaPullRequest request);

    /**
     * Deletes a model from the local Ollama server.
     *
     * @param request body containing the model name to delete.
     * @return raw {@link Response}; HTTP 200 on success, 404 if not found.
     */
    @DELETE
    @Path("/delete")
    Response deleteModel(OllamaDeleteRequest request);
}
