package ac.uk.sussex.kn253.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final Duration HEALTH_FAILURE_LOG_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration MIN_HEALTH_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration MAX_HEALTH_TIMEOUT = Duration.ofSeconds(10);

    private final Map<String, Instant> lastHealthFailureLogAt = new ConcurrentHashMap<>();

    private static final String MANUAL_RETRY_POLICY_NOTE = "Automatic retries are disabled by policy; retry only after explicit user confirmation.";

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
        return isHealthy(null);
    }

    /**
     * Returns {@code true} if the Ollama server at the provided base URL responds
     * with HTTP 200.
     */
    public boolean isHealthy(final String baseUrl) {
        String resolvedBaseUrl = null;
        try {
            resolvedBaseUrl = resolveBaseUrl(baseUrl);
            final String normalizedBaseUrl = normalizeBaseUrl(resolvedBaseUrl);
            final int statusCode = probeHealthStatus(normalizedBaseUrl);
            final boolean ok = statusCode == 200;
            LOG.fine(() -> String.format("Ollama health check for %s: %s (HTTP %d)",
                    normalizedBaseUrl,
                    ok ? "OK" : "FAIL",
                    statusCode));
            if (ok) {
                lastHealthFailureLogAt.remove(normalizedBaseUrl);
            }
            return ok;
        } catch (final Exception e) {
            final String targetBaseUrl = normalizeForLog(baseUrl, resolvedBaseUrl);
            if (shouldLogHealthFailure(targetBaseUrl)) {
                LOG.warning(() -> String.format("Ollama health check failed for %s: %s",
                        targetBaseUrl,
                        describeException(e)));
            }
            return false;
        }
    }

    private String normalizeForLog(final String requestedBaseUrl, final String resolvedBaseUrl) {
        if (resolvedBaseUrl != null && !resolvedBaseUrl.isBlank()) {
            return resolvedBaseUrl.trim();
        }
        if (requestedBaseUrl != null && !requestedBaseUrl.isBlank()) {
            return requestedBaseUrl.trim();
        }
        return "<unconfigured>";
    }

    private boolean shouldLogHealthFailure(final String targetBaseUrl) {
        final Instant now = Instant.now();
        final Instant previous = lastHealthFailureLogAt.get(targetBaseUrl);
        if (previous != null && Duration.between(previous, now).compareTo(HEALTH_FAILURE_LOG_COOLDOWN) < 0) {
            return false;
        }
        lastHealthFailureLogAt.put(targetBaseUrl, now);
        return true;
    }

    private String describeException(final Exception e) {
        final String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getName();
        }
        return e.getClass().getName() + ": " + message;
    }

    int probeHealthStatus(final String normalizedBaseUrl) throws Exception {
        final String endpoint = normalizedBaseUrl.endsWith("/")
                ? normalizedBaseUrl + "api/tags"
                : normalizedBaseUrl + "/api/tags";
        final Duration healthTimeout = resolveHealthTimeout();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(healthTimeout)
                .GET()
                .build();
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(healthTimeout)
                .build();
        final HttpResponse<Void> response = client
                .send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    private Duration resolveHealthTimeout() {
        final int configuredSeconds = config.timeoutSeconds();
        if (configuredSeconds <= 0) {
            return Duration.ofSeconds(5);
        }
        final Duration configured = Duration.ofSeconds(configuredSeconds);
        if (configured.compareTo(MIN_HEALTH_TIMEOUT) < 0) {
            return MIN_HEALTH_TIMEOUT;
        }
        if (configured.compareTo(MAX_HEALTH_TIMEOUT) > 0) {
            return MAX_HEALTH_TIMEOUT;
        }
        return configured;
    }

    private String normalizeBaseUrl(final String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Ollama base URL is not configured");
        }
        final String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Returns the Ollama server version string, or {@link Optional#empty()} if
     * the server is unreachable.
     */
    public Optional<String> getVersion() {
        try {
            return Optional.ofNullable(getClient(null).version());
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
        return listModels(null);
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
            LOG.warning(MANUAL_RETRY_POLICY_NOTE);
            return Collections.emptyList();
        }
    }

    /**
     * Returns models currently loaded in GPU/CPU memory.
     * Returns an empty list if the server is unreachable.
     */
    public List<OllamaRunningModel> listRunningModels() {
        try {
            final var response = getClient(null).listRunningModels();
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
     *                  {@code gemma4}.
     */
    public boolean isModelAvailable(final String modelName) {
        return isModelAvailable(null, modelName);
    }

    /**
     * Returns {@code true} if a model with the given name is locally available at
     * the provided base URL.
     *
     * @param baseUrl   Ollama base URL to query.
     * @param modelName fully-qualified model name.
     */
    public boolean isModelAvailable(final String baseUrl, final String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        return listModels(baseUrl).stream()
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
        return showModel(null, modelName);
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
     * @param modelName model to pull, e.g. {@code gemma4}.
     * @return the final {@link OllamaPullStatus}.
     * @throws OllamaException if the pull fails.
     */
    public OllamaPullStatus pullModel(final String modelName) {
        return pullModel(null, modelName);
    }

    /**
     * Pulls a model using the specified base URL, blocking until the download is
     * complete.
     *
     * @param baseUrl   Ollama base URL to use.
     * @param modelName model to pull, e.g. {@code gemma4}.
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
            LOG.warning(() -> String.format("Pull failed for model '%s'. %s", modelName, MANUAL_RETRY_POLICY_NOTE));
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

        final String configuredBaseUrl = resolveBaseUrl(baseUrl);
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
            LOG.warning(() -> String.format("Streaming pull failed for model '%s'. %s", modelName,
                    MANUAL_RETRY_POLICY_NOTE));
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
        deleteModel(null, modelName);
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
            LOG.warning(() -> String.format("Delete failed for model '%s'. %s", modelName,
                    MANUAL_RETRY_POLICY_NOTE));
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
        return ensureModelAvailable(null, modelName);
    }

    /**
     * Ensures that {@code modelName} is available on the provided Ollama base URL,
     * pulling it from the registry if needed.
     *
     * @param baseUrl   Ollama base URL to target.
     * @param modelName model to ensure is available.
     * @return {@code true} if the model was already present; {@code false} if it
     *         was pulled.
     * @throws OllamaException if the pull fails or the model is still unavailable.
     */
    public boolean ensureModelAvailable(final String baseUrl, final String modelName) {
        if (modelName == null || modelName.isBlank()) {
            throw new OllamaException("Model name must not be blank");
        }
        if (isModelAvailable(baseUrl, modelName)) {
            LOG.fine(() -> String.format("Model '%s' is already available.", modelName));
            return true;
        }
        final String resolvedBaseUrl = resolveBaseUrl(baseUrl);
        LOG.warning(() -> String.format(
                "Model '%s' not found locally at %s. Pulling now (this can take several minutes).",
                modelName,
                resolvedBaseUrl));
        final Instant pullStart = Instant.now();
        final OllamaPullStatus pullStatus = runWithProgressWarnings(
                () -> pullModel(baseUrl, modelName),
                "Still pulling Ollama model '%s' from %s ...",
                modelName,
                resolvedBaseUrl);
        final long elapsedSeconds = Duration.between(pullStart, Instant.now()).toSeconds();
        LOG.warning(() -> String.format(
                "Completed pull attempt for model '%s' from %s in %ds with status '%s'.",
                modelName,
                resolvedBaseUrl,
                elapsedSeconds,
                pullStatus != null ? pullStatus.getStatus() : "unknown"));
        if (!pullStatus.isSuccess() && !isModelAvailable(baseUrl, modelName)) {
            LOG.warning(() -> String.format("Model availability check failed for '%s'. %s", modelName,
                    MANUAL_RETRY_POLICY_NOTE));
            throw new OllamaException("Pull did not complete successfully for model '" + modelName + "'");
        }
        if (!isModelAvailable(baseUrl, modelName)) {
            LOG.warning(() -> String.format("Model '%s' still unavailable after pull. %s", modelName,
                    MANUAL_RETRY_POLICY_NOTE));
            throw new OllamaException("Model '" + modelName + "' is still unavailable after pull");
        }
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

    /**
     * Eagerly initializes Ollama client construction paths to avoid first-use
     * latency spikes during interactive menu actions.
     */
    public void warmUpClient(final String baseUrl) {
        try {
            getClient(baseUrl);
            objectMapper.getTypeFactory();
        } catch (final Exception e) {
            LOG.fine(() -> String.format("Ollama client warm-up skipped: %s", e.getMessage()));
        }
    }

    private OllamaPullStatus runWithProgressWarnings(
            final PullOperation operation,
            final String progressMessageTemplate,
            final String modelName,
            final String baseUrl) {
        final AtomicBoolean done = new AtomicBoolean(false);
        final Thread progressThread = new Thread(() -> {
            while (!done.get()) {
                try {
                    Thread.sleep(15000L);
                    if (!done.get()) {
                        LOG.warning(() -> String.format(progressMessageTemplate, modelName, baseUrl));
                    }
                } catch (final InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "ollama-model-pull-progress");
        progressThread.setDaemon(true);
        progressThread.start();

        try {
            return operation.run();
        } finally {
            done.set(true);
            progressThread.interrupt();
        }
    }

    @FunctionalInterface
    private interface PullOperation {
        OllamaPullStatus run();
    }

    OllamaManagementClient getClient(final String baseUrl) {
        final String resolvedBaseUrl = resolveBaseUrl(baseUrl);
        if (resolvedBaseUrl == null || resolvedBaseUrl.isBlank()) {
            throw new IllegalStateException("Ollama base URL is not configured");
        }
        return buildClient(resolvedBaseUrl);
    }

    String resolveBaseUrl(final String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.trim();
        }
        return config.baseUrl().orElse(null);
    }

    OllamaManagementClient buildClient(final String resolvedBaseUrl) {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(resolvedBaseUrl))
                .build(OllamaManagementClient.class);
    }
}
