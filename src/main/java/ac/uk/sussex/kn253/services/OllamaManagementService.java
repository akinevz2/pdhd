package ac.uk.sussex.kn253.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.eclipse.microprofile.rest.client.RestClientBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.ollama.*;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
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

    private static final Logger LOG = Logger.getLogger(OllamaManagementService.class.getName());

    @Inject
    OllamaConfig config;

    @Inject
    ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Health
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the Ollama server responds with HTTP 200.
     */
    public boolean isHealthy() {
        try {
            final Response response = getClient(config.baseUrl()).health();
            final boolean ok = response.getStatus() == 200;
            LOG.fine(
                    () -> String.format("Ollama health check: %s (HTTP %d)", ok ? "OK" : "FAIL", response.getStatus()));
            return ok;
        } catch (final Exception e) {
            LOG.warning(() -> String.format("Ollama health check failed: %s", e.getMessage()));
            return false;
        }
    }

    /**
     * Returns {@code true} if the Ollama server at the provided base URL responds
     * with HTTP 200.
     */
    public boolean isHealthy(final String baseUrl) {
        try {
            final Response response = getClient(baseUrl).health();
            final boolean ok = response.getStatus() == 200;
            LOG.fine(() -> String.format("Ollama health check for %s: %s (HTTP %d)",
                    baseUrl,
                    ok ? "OK" : "FAIL",
                    response.getStatus()));
            return ok;
        } catch (final Exception e) {
            LOG.warning(() -> String.format("Ollama health check failed for %s: %s", baseUrl, e.getMessage()));
            return false;
        }
    }

    /**
     * Returns the Ollama server version string, or {@link Optional#empty()} if
     * the server is unreachable.
     */
    public Optional<String> getVersion() {
        try {
            return Optional.ofNullable(getClient(config.baseUrl()).version());
        } catch (final Exception e) {
            LOG.warning(() -> String.format("Could not retrieve Ollama version: %s", e.getMessage()));
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
        return listModels(config.baseUrl());
    }

    /**
     * Returns all models available on the Ollama server at the provided base URL.
     * Falls back to the injected default client when {@code baseUrl} is blank.
     */
    public List<OllamaModelInfo> listModels(final String baseUrl) {
        try {
            final var response = getClient(baseUrl).listModels();
            return response.getModels() != null ? response.getModels() : Collections.emptyList();
        } catch (final Exception e) {
            LOG.severe(() -> String.format("Failed to list Ollama models: %s", e.getMessage()));
            return Collections.emptyList();
        }
    }

    /**
     * Returns models currently loaded in GPU/CPU memory.
     * Returns an empty list if the server is unreachable.
     */
    public List<OllamaRunningModel> listRunningModels() {
        try {
            final var response = getClient(config.baseUrl()).listRunningModels();
            return response.getModels() != null ? response.getModels() : Collections.emptyList();
        } catch (final Exception e) {
            LOG.severe(() -> String.format("Failed to list running Ollama models: %s", e.getMessage()));
            return Collections.emptyList();
        }
    }

    /**
     * Returns {@code true} if a model with the given name is locally available.
     *
     * @param modelName fully-qualified model name, e.g.
     *                  {@code llama3.1:8b-instruct-q4_K_M}.
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
        return showModel(config.baseUrl(), modelName);
    }

    public Optional<OllamaShowResponse> showModel(final String baseUrl, final String modelName) {
        try {
            return Optional.of(getClient(baseUrl).showModel(new OllamaShowRequest(modelName)));
        } catch (final Exception e) {
            LOG.warning(() -> String.format("Could not show model '%s': %s", modelName, e.getMessage()));
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
        return pullModel(config.baseUrl(), modelName);
    }

    /**
     * Pulls a model using the specified base URL, blocking until the download is
     * complete.
     *
     * @param baseUrl   Ollama base URL to use.
     * @param modelName model to pull, e.g. {@code llama3.1:8b-instruct-q4_K_M}.
     * @return the final {@link OllamaPullStatus}.
     * @throws OllamaException if the pull fails.
     */
    public OllamaPullStatus pullModel(final String baseUrl, final String modelName) {
        LOG.info(() -> String.format("Pulling Ollama model '%s' ...", modelName));
        try {
            final var request = new OllamaPullRequest(modelName, false, null);
            final OllamaPullStatus status = getClient(baseUrl).pullModel(request);
            if (status.isSuccess()) {
                LOG.info(() -> String.format("Model '%s' pulled successfully.", modelName));
            } else {
                LOG.warning(
                        () -> String.format("Pull of '%s' finished with status: %s", modelName, status.getStatus()));
            }
            return status;
        } catch (final Exception e) {
            throw new OllamaException("Failed to pull model '" + modelName + "'", e);
        }
    }

    /**
     * Pulls a model with Ollama streaming enabled and reports incremental
     * progress events to the provided callback.
     *
     * @param baseUrl          Ollama base URL to use.
     * @param modelName        model to pull.
     * @param progressConsumer callback invoked for every status event.
     * @return the final pull status.
     */
    public OllamaPullStatus pullModelStreaming(final String baseUrl, final String modelName,
            final Consumer<OllamaPullStatus> progressConsumer) {
        if (modelName == null || modelName.isBlank()) {
            throw new OllamaException("Model name must not be blank");
        }
        if (progressConsumer == null) {
            throw new OllamaException("Progress consumer must not be null");
        }

        final String configuredBaseUrl = (baseUrl == null || baseUrl.isBlank()) ? config.baseUrl() : baseUrl.trim();
        final String endpoint = configuredBaseUrl.endsWith("/")
                ? configuredBaseUrl + "api/pull"
                : configuredBaseUrl + "/api/pull";

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(new OllamaPullRequest(modelName, true, null));
        } catch (final Exception e) {
            throw new OllamaException("Failed to encode pull request for '" + modelName + "'", e);
        }

        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        try {
            final HttpResponse<java.io.InputStream> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 300) {
                throw new OllamaException(
                        "Failed to pull model '" + modelName + "' (HTTP " + response.statusCode() + ")");
            }

            OllamaPullStatus lastStatus = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    final OllamaPullStatus status = objectMapper.readValue(line, OllamaPullStatus.class);
                    lastStatus = status;
                    progressConsumer.accept(status);
                }
            }

            if (lastStatus == null) {
                throw new OllamaException("Pull stream ended without status for model '" + modelName + "'");
            }
            return lastStatus;
        } catch (final OllamaException e) {
            throw e;
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
        deleteModel(config.baseUrl(), modelName);
    }

    public void deleteModel(final String baseUrl, final String modelName) {
        LOG.info(() -> String.format("Deleting Ollama model '%s' ...", modelName));
        try {
            final Response response = getClient(baseUrl).deleteModel(new OllamaDeleteRequest(modelName));
            if (response.getStatus() >= 300) {
                throw new WebApplicationException("Unexpected status: " + response.getStatus(), response);
            }
            LOG.info(() -> String.format("Model '%s' deleted.", modelName));
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
            LOG.fine(() -> String.format("Model '%s' is already available.", modelName));
            return true;
        }
        LOG.info(() -> String.format("Model '%s' not found locally - pulling ...", modelName));
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

    OllamaManagementClient getClient(final String baseUrl) {
        final String resolvedBaseUrl = (baseUrl == null || baseUrl.isBlank())
                ? config.baseUrl()
                : baseUrl.trim();
        if (resolvedBaseUrl == null || resolvedBaseUrl.isBlank()) {
            throw new IllegalStateException("Ollama base URL is not configured");
        }
        return buildClient(resolvedBaseUrl);
    }

    OllamaManagementClient buildClient(final String resolvedBaseUrl) {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(resolvedBaseUrl))
                .build(OllamaManagementClient.class);
    }
}
