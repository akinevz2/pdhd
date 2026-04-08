package ac.uk.sussex.kn253.services;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OllamaRuntimeEndpointService {

    @Inject
    OllamaConfig config;

    private volatile String runtimeBaseUrl;

    public String getActiveBaseUrl() {
        final String currentRuntime = runtimeBaseUrl;
        if (currentRuntime != null && !currentRuntime.isBlank()) {
            return normalizeBaseUrl(currentRuntime);
        }
        return normalizeBaseUrl(config.baseUrl());
    }

    public boolean hasRuntimeOverride() {
        final String currentRuntime = runtimeBaseUrl;
        return currentRuntime != null && !currentRuntime.isBlank();
    }

    public void setRuntimeBaseUrl(final String baseUrl) {
        runtimeBaseUrl = normalizeBaseUrl(baseUrl);
    }

    public String resolveExplicitOrActive(final String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return normalizeBaseUrl(baseUrl);
        }
        return getActiveBaseUrl();
    }

    public String resolvePersistedOrActive(final String persistedBaseUrl) {
        if (hasRuntimeOverride()) {
            return getActiveBaseUrl();
        }
        if (persistedBaseUrl != null && !persistedBaseUrl.isBlank()) {
            return normalizeBaseUrl(persistedBaseUrl);
        }
        return normalizeBaseUrl(config.baseUrl());
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
}