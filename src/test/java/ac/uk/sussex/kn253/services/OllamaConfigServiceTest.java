package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.model.OllamaSettings;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class OllamaConfigServiceTest {

    @Inject
    OllamaConfigService ollamaConfigService;

    @BeforeEach
    @Transactional
    void resetSettings() {
        OllamaSettings.deleteAll();
        System.clearProperty("quarkus.langchain4j.ollama.base-url");
        System.clearProperty("quarkus.langchain4j.ollama.chat-model.model-id");
    }

    @Test
    void loadSeedsLangchainSystemPropertiesFromResolvedDefaults() {
        final OllamaSettings loaded = ollamaConfigService.load();

        assertEquals("http://desktop-box26:11434", loaded.getBaseUrl());
        assertEquals("http://desktop-box26:11434", System.getProperty("quarkus.langchain4j.ollama.base-url"));
        assertEquals(loaded.getModelName(), System.getProperty("quarkus.langchain4j.ollama.chat-model.model-id"));
    }

    @Test
    @Transactional
    void saveUpdatesLangchainSystemPropertiesFromPersistedSettings() {
        final OllamaSettings settings = ollamaConfigService.load();
        settings.setBaseUrl("http://localhost:11434");
        settings.setModelName("llama3.2:latest");

        ollamaConfigService.save(settings);

        assertEquals("http://localhost:11434", System.getProperty("quarkus.langchain4j.ollama.base-url"));
        assertEquals("llama3.2:latest", System.getProperty("quarkus.langchain4j.ollama.chat-model.model-id"));
    }
}
