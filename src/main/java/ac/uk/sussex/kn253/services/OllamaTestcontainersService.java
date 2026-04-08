package ac.uk.sussex.kn253.services;

import java.util.logging.Logger;

import org.testcontainers.ollama.OllamaContainer;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OllamaTestcontainersService {

    private static final Logger LOG = Logger.getLogger(OllamaTestcontainersService.class.getName());

    @Inject
    OllamaConfig config;

    private final Object lock = new Object();
    private OllamaContainer container;

    public boolean isUsingTestcontainers() {
        synchronized (lock) {
            return container != null && container.isRunning();
        }
    }

    public String getRunningEndpointOrNull() {
        synchronized (lock) {
            if (container == null || !container.isRunning()) {
                return null;
            }
            return normalize(container.getEndpoint());
        }
    }

    public String startAndGetEndpoint() {
        synchronized (lock) {
            if (container != null && container.isRunning()) {
                return normalize(container.getEndpoint());
            }

            final OllamaContainer candidate = new OllamaContainer(config.ollamaImage());
            try {
                LOG.warning(() -> String.format(
                        "Configured Ollama endpoint is unreachable. Starting Testcontainers Ollama image '%s'.",
                        config.ollamaImage()));
                candidate.start();
                container = candidate;
                final String endpoint = normalize(container.getEndpoint());
                LOG.warning(() -> String.format("Using Testcontainers Ollama endpoint: %s", endpoint));
                return endpoint;
            } catch (final RuntimeException e) {
                try {
                    if (candidate.isRunning()) {
                        candidate.stop();
                    }
                } catch (final RuntimeException cleanupError) {
                    LOG.warning(
                            () -> "Failed to clean up failed Ollama container startup: " + cleanupError.getMessage());
                }
                throw new IllegalStateException("Failed to start Testcontainers Ollama", e);
            }
        }
    }

    @PreDestroy
    void stopIfRunning() {
        stopRunningContainer();
    }

    public void stopRunningContainer() {
        synchronized (lock) {
            if (container == null) {
                return;
            }
            try {
                if (container.isRunning()) {
                    container.stop();
                }
            } catch (final RuntimeException e) {
                LOG.warning(() -> "Failed to stop Testcontainers Ollama cleanly: " + e.getMessage());
            } finally {
                container = null;
            }
        }
    }

    private String normalize(final String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Testcontainers Ollama did not expose a valid endpoint");
        }
        final String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}