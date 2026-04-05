package ac.uk.sussex.kn253.services;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ModelConfigService {

    private static final Logger LOG = Logger.getLogger(ModelConfigService.class.getName());
    private static final TypeReference<List<OllamaModelInfo>> MODEL_LIST_TYPE = new TypeReference<>() {
    };

    @Inject
    EntityManager em;

    @Inject
    OllamaConfig ollamaConfig;

    @Inject
    OllamaManagementService ollamaManagementService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Returns the persisted settings row, creating one with defaults if none
     * exists.
     */
    @Transactional
    public LLMSettings load() {
        return LLMSettings.<LLMSettings>listAll().stream()
                .findFirst()
                .orElseGet(this::createDefaults);
    }

    /**
     * Merges the given settings back into the database.
     */
    @Transactional
    public void save(final LLMSettings settings) {
        em.merge(settings);
    }

    /**
     * Queries live models from Ollama, writes the result into the settings cache,
     * and returns the list. Falls back to the cached list if Ollama is unreachable.
     */
    @Transactional
    public List<OllamaModelInfo> refreshModelCache() {
        final LLMSettings settings = load();
        final List<OllamaModelInfo> live = ollamaManagementService.listModels(settings.getBaseUrl());
        if (!live.isEmpty()) {
            settings.setOllamaModelsJson(toJson(live));
            em.merge(settings);
            return live;
        }
        return readModelsJson(settings.getOllamaModelsJson());
    }

    /**
     * Returns the model list stored in the settings JSON cache without hitting
     * Ollama.
     */
    @Transactional
    public List<OllamaModelInfo> getCachedModels() {
        return readModelsJson(load().getOllamaModelsJson());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private LLMSettings createDefaults() {
        final LLMSettings defaults = new LLMSettings();
        try {
            defaults.setBaseUrl(ollamaConfig.baseUrl());
            defaults.setModelName(ollamaConfig.modelName());
            defaults.setEmbeddingModelName(ollamaConfig.embeddingModelName());
        } catch (final Exception e) {
            LOG.warning(() -> "OllamaConfig unavailable during createDefaults: " + e.getMessage());
        }
        defaults.setSystemPrompt(LLMSettings.DEFAULT_SYSTEM_PROMPT);
        defaults.setToolSystemPrompt(LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
        defaults.persist();
        return defaults;
    }

    private List<OllamaModelInfo> readModelsJson(final String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            final List<OllamaModelInfo> models = objectMapper.readValue(json, MODEL_LIST_TYPE);
            return models != null ? models : Collections.emptyList();
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to parse cached models JSON: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private String toJson(final List<OllamaModelInfo> models) {
        try {
            return objectMapper.writeValueAsString(models);
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to serialize model cache: " + e.getMessage());
            return "[]";
        }
    }
}
