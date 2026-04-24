package ac.uk.sussex.kn253.services;

import java.util.*;
import java.util.logging.Logger;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Manages Ollama runtime configuration, model lists, and settings
 * serialization for the menu API.
 */
@ApplicationScoped
public class OllamaConfigurationService {

    private static final Logger LOG = Logger.getLogger(OllamaConfigurationService.class.getName());
    private static final String DOCKER_INTERNAL_URL = "http://host.docker.internal:11434";

    @Inject
    OllamaConfig ollamaConfig;

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    OllamaManagementService ollamaManagementService;

    public record OllamaSettingField(
            String key,
            String label,
            String inputType,
            String hint,
            Double min,
            Double max,
            Double step,
            boolean modelField) {
    }

    public record OllamaSettingsResponse(
            Map<String, Object> settings,
            List<OllamaSettingField> settingFields,
            String baseUrl,
            String modelName,
            int timeoutSeconds,
            double temperature,
            int numPredict,
            int numCtx,
            String systemPrompt,
            String defaultSystemPrompt,
            String toolSystemPrompt,
            String defaultToolSystemPrompt) {
    }

    public OllamaSettingsResponse buildOllamaSettingsResponse(final LLMSettings settings) {
        final Map<String, Object> settingsMap = new LinkedHashMap<>();
        settingsMap.put("baseUrl", settings.getBaseUrl());
        settingsMap.put("modelName", settings.getModelName());
        settingsMap.put("embeddingModelName", settings.getEmbeddingModelName());

        final List<OllamaSettingField> fields = List.of(
                new OllamaSettingField("baseUrl", "Ollama Base URL", "text", "The HTTP URL to your Ollama instance",
                        null, null, null, false),
                new OllamaSettingField("modelName", "Chat Model", "text",
                        "Model used for chat interactions", null, null, null, true),
                new OllamaSettingField("embeddingModelName", "Embedding Model", "text",
                        "Model used for embeddings", null, null, null, false));

        return new OllamaSettingsResponse(
                settingsMap,
                fields,
                settings.getBaseUrl(),
                settings.getModelName(),
                ollamaConfig.timeoutSeconds(),
                0.7,
                256,
                2048,
                settings.getSystemPrompt(),
                LLMSettings.DEFAULT_SYSTEM_PROMPT,
                settings.getToolSystemPrompt(),
                LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
    }

    public String resolveRuntimeEndpoint() {
        try {
            final LLMSettings settings = modelConfigService.load();
            final String persistedBaseUrl = settings != null ? settings.getBaseUrl() : null;
            if (persistedBaseUrl != null && !persistedBaseUrl.isBlank()) {
                final String trimmed = persistedBaseUrl.trim();
                if (ollamaManagementService.isHealthy(trimmed)) {
                    return trimmed;
                }
                LOG.fine(() -> "Persisted Ollama endpoint unreachable: " + trimmed + "; falling back to config");
            }
        } catch (final Exception e) {
            LOG.fine(() -> "Skipping persisted runtime endpoint lookup: " + e.getMessage());
        }

        return ollamaConfig.baseUrl()
                .map(String::trim)
                .filter(baseUrl -> !baseUrl.isBlank())
                .orElse(DOCKER_INTERNAL_URL);
    }

    public String resolveRuntimeProvider(final String runtimeEndpoint) {
        if (runtimeEndpoint == null || runtimeEndpoint.isBlank()) {
            return "EXTERNAL";
        }
        return DOCKER_INTERNAL_URL.equals(runtimeEndpoint) ? "INTERNAL" : "EXTERNAL";
    }

    public String normalizeBaseUrl(final String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        final String trimmed = baseUrl.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public List<String> listModelNames(final String baseUrl) {
        final List<OllamaModelInfo> models = baseUrl == null
                ? modelConfigService.refreshModelCache()
                : ollamaManagementService.listModels(baseUrl);
        return models.stream()
                .map(OllamaModelInfo::getName)
                .toList();
    }
}
