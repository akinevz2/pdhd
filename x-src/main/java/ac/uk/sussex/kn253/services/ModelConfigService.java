package ac.uk.sussex.kn253.services;

import java.util.*;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.entities.LLMSettings;
import ac.uk.sussex.kn253.entities.ollama.OllamaModelInfo;
import ac.uk.sussex.kn253.model.ollama.OllamaConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Loads and saves the single {@link LLMSettings} record that drives the
 * Ollama integration at runtime.
 */
@ApplicationScoped
public class ModelConfigService {

    private static final Logger LOG = Logger.getLogger(ModelConfigService.class.getName());
    private static final String OLLAMA_BASE_URL_PROPERTY = "quarkus.langchain4j.ollama.base-url";
    private static final String OLLAMA_MODEL_ID_PROPERTY = "quarkus.langchain4j.ollama.chat-model.model-id";
    private static final TypeReference<List<OllamaModelInfo>> MODEL_INFO_LIST = new TypeReference<>() {
    };

    @Inject
    EntityManager em;

    @Inject
    OllamaConfig ollamaConfig;

    @Inject
    OllamaManagementService ollamaManagementService;

    @Inject
    ObjectMapper objectMapper;

    private OllamaModelInfo modelInfo;
    @Inject
    Event<LLMSettings> settingsChangedEvent;

    @ConfigProperty(name = "pdhd.model-reload.enabled", defaultValue = "true")
    boolean modelReloadEnabled;

    private OllamaModelInfo embeddingModelInfo;

    /**
     * Returns the persisted settings, creating and persisting a default row if
     * none exists yet.
     */
    @Transactional
    public LLMSettings load() {
        final LLMSettings settings = LLMSettings.<LLMSettings>listAll()
                .stream()
                .findFirst()
                .orElseGet(this::createDefaults);

        boolean dirty = false;
        if (settings.getSystemPrompt() == null || settings.getSystemPrompt().isBlank()) {
            settings.setSystemPrompt(LLMSettings.DEFAULT_SYSTEM_PROMPT);
            dirty = true;
        }

        if (settings.getToolSystemPrompt() == null || settings.getToolSystemPrompt().isBlank()) {
            settings.setToolSystemPrompt(LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
            dirty = true;
        }

        if (dirty) {
            settings.persistAndFlush();
        }

        update(settings);

        return settings;
    }

    /** Persists (inserts or updates) the given settings. */
    @Transactional
    public void save(final LLMSettings settings) {
        final LLMSettings merged = em.merge(settings);
        update(merged);
        if (modelReloadEnabled) {
            settingsChangedEvent.fire(merged);
        }
    }

    private void update(final LLMSettings settings) {
        if (settings == null) {
            return;
        }

        final String baseUrl = settings.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            System.setProperty(OLLAMA_BASE_URL_PROPERTY, baseUrl.trim());
        }

        final String modelName = settings.getModelName();
        if (modelName != null && !modelName.isBlank()) {
            System.setProperty(OLLAMA_MODEL_ID_PROPERTY, modelName.trim());
        }

        modelInfo = resolveSelectedModelInfo(settings);

    }

    private LLMSettings createDefaults() {
        final LLMSettings defaults = new LLMSettings();
        if (ollamaConfig != null) {
            try {
                defaults.setBaseUrl(ollamaConfig.baseUrl());
                defaults.setModelName(ollamaConfig.modelName());
                defaults.setEmbeddingModelName(ollamaConfig.embeddingModelName());
            } catch (final Exception e) {
                // OllamaConfig proxy not available in current context (e.g. deep transactional
                // call during tests) — fall back to the entity's built-in scalar defaults
                LOG.warning(() -> String.format("OllamaConfig unavailable in createDefaults, using entity defaults: %s",
                        e.getMessage()));
            }
        }
        defaults.persist();
        return defaults;
    }

    public OllamaModelInfo getModelInfo() {
        return modelInfo;
    }

    public OllamaModelInfo getEmbeddingModelInfo() {
        return embeddingModelInfo;
    }

    @Transactional
    public List<OllamaModelInfo> refreshModelCache() {
        final LLMSettings settings = load();
        final List<OllamaModelInfo> models = ollamaManagementService.listModels(settings.getBaseUrl());
        if (models.isEmpty()) {
            return getCachedModels();
        }

        settings.setOllamaModelsJson(writeModelsJson(models));
        settings.persistAndFlush();
        update(settings);
        return models;
    }

    @Transactional
    public List<OllamaModelInfo> getCachedModels() {
        final LLMSettings settings = load();
        return readModelsJson(settings.getOllamaModelsJson());
    }

    private OllamaModelInfo resolveSelectedModelInfo(final LLMSettings settings) {
        final String selectedModel = settings.getModelName();
        if (selectedModel == null || selectedModel.isBlank()) {
            return null;
        }

        final var selected = readModelsJson(settings.getOllamaModelsJson()).stream()
                .filter(Objects::nonNull)
                .filter(model -> selectedModel.equals(model.getName()) || selectedModel.equals(model.getModel()))
                .findFirst();

        if (selected.isPresent()) {
            return selected.get();
        }
        return null;
    }

    private List<OllamaModelInfo> readModelsJson(final String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            final List<OllamaModelInfo> models = objectMapper.readValue(json, MODEL_INFO_LIST);
            return models != null ? models : Collections.emptyList();
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to parse cached Ollama models JSON: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private String writeModelsJson(final List<OllamaModelInfo> models) {
        try {
            return objectMapper.writeValueAsString(models);
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to serialize Ollama model cache: " + e.getMessage());
            return "[]";
        }
    }

}