package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.*;

import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.testsupport.OllamaTestSupport;
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
        final String baseUrl = OllamaTestSupport.testBaseUrl();
        Assumptions.assumeTrue(
                OllamaTestSupport.isReachable(baseUrl),
                () -> "Skipping: workstation Ollama not reachable at " + baseUrl);

        final OllamaSettings loaded = ollamaConfigService.load();

        assertEquals(baseUrl, loaded.getBaseUrl());
        assertEquals(baseUrl, System.getProperty("quarkus.langchain4j.ollama.base-url"));
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
