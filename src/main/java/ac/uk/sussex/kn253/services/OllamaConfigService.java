package ac.uk.sussex.kn253.services;

import ac.uk.sussex.kn253.model.OllamaSettings;
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

    @Inject
    EntityManager em;

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

        return settings;
    }

    /** Persists (inserts or updates) the given settings. */
    @Transactional
    public void save(final OllamaSettings settings) {
        em.merge(settings);
    }

    private OllamaSettings createDefaults() {
        final OllamaSettings defaults = new OllamaSettings();
        defaults.persist();
        return defaults;
    }
}
