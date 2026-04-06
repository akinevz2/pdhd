package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.LLMSettings;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class ModelConfigServicePersistenceTest {

    @Inject
    ModelConfigService modelConfigService;

    @Test
    @Transactional
    void savePersistsBaseUrlModelAndEmbeddingModel() {
        LLMSettings.deleteAll();

        final LLMSettings settings = modelConfigService.load();
        settings.setBaseUrl("http://desktop-box26.local:11434");
        settings.setModelName("llama3.2");
        settings.setEmbeddingModelName("qwen3-embedding");

        modelConfigService.save(settings);

        final LLMSettings reloaded = modelConfigService.load();
        assertNotNull(reloaded.id);
        assertEquals("http://desktop-box26.local:11434", reloaded.getBaseUrl());
        assertEquals("llama3.2", reloaded.getModelName());
        assertEquals("qwen3-embedding", reloaded.getEmbeddingModelName());
    }
}