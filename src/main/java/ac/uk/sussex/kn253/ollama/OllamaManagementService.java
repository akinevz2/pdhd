package ac.uk.sussex.kn253.ollama;

import java.util.*;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.ollama.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * CDI service that wraps {@link OllamaManagementClient} and provides
 * higher-level operations for managing an Ollama instance.
 *
 * <p>
 * Inject this bean wherever you need to:
 * <ul>
 * <li>Check whether the Ollama server is reachable</li>
 * <li>List, inspect, pull, or delete models</li>
 * <li>Query which models are currently loaded in memory</li>
 * <li>Ensure a required model is present before starting inference</li>
 * </ul>
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * @Inject
 * OllamaManagementService ollama;
 *
 * // Ensure the configured model is available, pulling it if necessary
 * ollama.ensureModelAvailable(config.modelName());
 *
 * // List all local models
 * ollama.listModels().forEach(m -> System.out.println(m.getName()));
 * }</pre>
 */
@ApplicationScoped
public class OllamaManagementService {

    private static final Logger LOG = Logger.getLogger(OllamaManagementService.class);

    @Inject
    @RestClient
    OllamaManagementClient client;

    @Inject
    OllamaConfig config;

    // -------------------------------------------------------------------------
    // Health
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the Ollama server responds with HTTP 200.
     */
    public boolean isHealthy() {
        try {
            final Response response = client.health();
            final boolean ok = response.getStatus() == 200;
            LOG.debugf("Ollama health check: %s (HTTP %d)", ok ? "OK" : "FAIL", response.getStatus());
            return ok;
        } catch (final Exception e) {
            LOG.warnf("Ollama health check failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Returns the Ollama server version string, or {@link Optional#empty()} if
     * the server is unreachable.
     */
    public Optional<String> getVersion() {
        try {
            return Optional.ofNullable(client.version());
        } catch (final Exception e) {
            LOG.warnf("Could not retrieve Ollama version: %s", e.getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Model listing
    // -------------------------------------------------------------------------

    /**
     * Returns all models that are locally available on the Ollama server.
     * Returns an empty list if the server is unreachable.
     */
    public List<OllamaModelInfo> listModels() {
        try {
            final var response = client.listModels();
            return response.getModels() != null ? response.getModels() : Collections.emptyList();
        } catch (final Exception e) {
            LOG.errorf("Failed to list Ollama models: %s", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Returns models currently loaded in GPU/CPU memory.
     * Returns an empty list if the server is unreachable.
     */
    public List<OllamaRunningModel> listRunningModels() {
        try {
            final var response = client.listRunningModels();
            return response.getModels() != null ? response.getModels() : Collections.emptyList();
        } catch (final Exception e) {
            LOG.errorf("Failed to list running Ollama models: %s", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Returns {@code true} if a model with the given name is locally available.
     *
    * @param modelName fully-qualified model name, e.g. {@code llama3.1:8b-instruct-q4_K_M}.
     */
    public boolean isModelAvailable(final String modelName) {
        return listModels().stream()
                .anyMatch(m -> modelName.equals(m.getName()) || modelName.equals(m.getModel()));
    }

    // -------------------------------------------------------------------------
    // Model inspection
    // -------------------------------------------------------------------------

    /**
     * Returns detailed information about a model.
     *
     * @param modelName fully-qualified model name.
     * @return {@link Optional} containing the show response, or empty on error.
     */
    public Optional<OllamaShowResponse> showModel(final String modelName) {
        try {
            return Optional.of(client.showModel(new OllamaShowRequest(modelName)));
        } catch (final Exception e) {
            LOG.warnf("Could not show model '%s': %s", modelName, e.getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Model lifecycle
    // -------------------------------------------------------------------------

    /**
     * Pulls a model from the Ollama registry, blocking until the download is
     * complete.
     *
    * @param modelName model to pull, e.g. {@code llama3.1:8b-instruct-q4_K_M}.
     * @return the final {@link OllamaPullStatus}.
     * @throws OllamaException if the pull fails.
     */
    public OllamaPullStatus pullModel(final String modelName) {
        LOG.infof("Pulling Ollama model '%s' …", modelName);
        try {
            final var request = new OllamaPullRequest(modelName, false, null);
            final OllamaPullStatus status = client.pullModel(request);
            if (status.isSuccess()) {
                LOG.infof("Model '%s' pulled successfully.", modelName);
            } else {
                LOG.warnf("Pull of '%s' finished with status: %s", modelName, status.getStatus());
            }
            return status;
        } catch (final Exception e) {
            throw new OllamaException("Failed to pull model '" + modelName + "'", e);
        }
    }

    /**
     * Deletes a model from the local Ollama server.
     *
     * @param modelName fully-qualified model name to delete.
     * @throws OllamaException if the server returns a non-2xx response.
     */
    public void deleteModel(final String modelName) {
        LOG.infof("Deleting Ollama model '%s' …", modelName);
        try {
            final Response response = client.deleteModel(new OllamaDeleteRequest(modelName));
            if (response.getStatus() >= 300) {
                throw new WebApplicationException("Unexpected status: " + response.getStatus(), response);
            }
            LOG.infof("Model '%s' deleted.", modelName);
        } catch (final OllamaException e) {
            throw e;
        } catch (final Exception e) {
            throw new OllamaException("Failed to delete model '" + modelName + "'", e);
        }
    }

    // -------------------------------------------------------------------------
    // Convenience helpers
    // -------------------------------------------------------------------------

    /**
     * Ensures that {@code modelName} is available locally, pulling it from the
     * registry if it is not already present.
     *
     * @param modelName model to ensure is available.
     * @return {@code true} if the model was already present; {@code false} if it
     *         was pulled.
     * @throws OllamaException if the pull fails.
     */
    public boolean ensureModelAvailable(final String modelName) {
        if (isModelAvailable(modelName)) {
            LOG.debugf("Model '%s' is already available.", modelName);
            return true;
        }
        LOG.infof("Model '%s' not found locally – pulling …", modelName);
        pullModel(modelName);
        return false;
    }

    /**
     * Convenience overload that uses the model name from {@link OllamaConfig}.
     *
     * @return {@code true} if the model was already present.
     */
    public boolean ensureDefaultModelAvailable() {
        return ensureModelAvailable(config.modelName());
    }
}
