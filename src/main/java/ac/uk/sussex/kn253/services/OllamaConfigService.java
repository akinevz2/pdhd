package ac.uk.sussex.kn253.services;

import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.ollama.OllamaConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Loads and saves the single {@link OllamaSettings} record that drives the
 * Ollama integration at runtime.
 */
@ApplicationScoped
public class OllamaConfigService {

    private static final Logger LOG = Logger.getLogger(OllamaConfigService.class);

    @Inject
    EntityManager em;

    @Inject
    OllamaConfig ollamaConfig;

    /**
     * Returns the persisted settings, creating and persisting a default row if
     * none exists yet.
     */
    @Transactional
    public OllamaSettings load() {
        final OllamaSettings settings = OllamaSettings.<OllamaSettings>listAll()
                .stream()
                .findFirst()
                .orElseGet(this::createDefaults);

        boolean dirty = false;
        if (settings.getSystemPrompt() == null || settings.getSystemPrompt().isBlank()) {
            settings.setSystemPrompt(OllamaSettings.DEFAULT_SYSTEM_PROMPT);
            dirty = true;
        }

        if (settings.getToolSystemPrompt() == null || settings.getToolSystemPrompt().isBlank()) {
            settings.setToolSystemPrompt(OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
            dirty = true;
        }

        if (dirty) {
            settings.persistAndFlush();
        }

        applyLangchainRuntimeOverrides(settings);

        return settings;
    }

    /** Persists (inserts or updates) the given settings. */
    @Transactional
    public void save(final OllamaSettings settings) {
        final OllamaSettings merged = em.merge(settings);
        applyLangchainRuntimeOverrides(merged);
    }

    private void applyLangchainRuntimeOverrides(final OllamaSettings settings) {
        if (settings == null) {
            return;
        }

        final String baseUrl = settings.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            System.setProperty("quarkus.langchain4j.ollama.base-url", baseUrl.trim());
        }

        final String modelName = settings.getModelName();
        if (modelName != null && !modelName.isBlank()) {
            System.setProperty("quarkus.langchain4j.ollama.chat-model.model-id", modelName.trim());
        }
    }

    private OllamaSettings createDefaults() {
        final OllamaSettings defaults = new OllamaSettings();
        if (ollamaConfig != null) {
            try {
                defaults.setBaseUrl(ollamaConfig.baseUrl());
                defaults.setModelName(ollamaConfig.modelName());
                defaults.setTimeoutSeconds(ollamaConfig.timeoutSeconds());
                defaults.setTemperature(ollamaConfig.temperature());
                defaults.setNumPredict(ollamaConfig.numPredict());
                defaults.setNumCtx(ollamaConfig.numCtx());
                defaults.setEmbeddingEnabled(ollamaConfig.embeddingEnabled());
                defaults.setEmbeddingModel(ollamaConfig.embeddingModelName());
                defaults.setEmbeddingDimension(ollamaConfig.embeddingDimension());
                defaults.setEmbeddingMaxResults(ollamaConfig.embeddingMaxResults());
            } catch (final Exception e) {
                // OllamaConfig proxy not available in current context (e.g. deep transactional
                // call during tests) — fall back to the entity's built-in scalar defaults
                LOG.warnf("OllamaConfig unavailable in createDefaults, using entity defaults: %s", e.getMessage());
            }
        }
        defaults.persist();
        return defaults;
    }
}
