package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.entities.LLMSettings;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class ModelConfigServiceTest {

    private static final String OLLAMA_BASE_URL_PROPERTY = "quarkus.langchain4j.ollama.base-url";
    private static final String OLLAMA_MODEL_ID_PROPERTY = "quarkus.langchain4j.ollama.chat-model.model-id";

    @Inject
    ModelConfigService modelConfigService;

    @Test
    @Transactional
    void loadCreatesSingleDefaultSettingsRowWhenMissing() {
        clearState();

        final LLMSettings loaded = modelConfigService.load();

        assertNotNull(loaded);
        assertNotNull(loaded.id);
        assertEquals(1L, LLMSettings.count());
        assertEquals(LLMSettings.DEFAULT_SYSTEM_PROMPT, loaded.getSystemPrompt());
        assertEquals(LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT, loaded.getToolSystemPrompt());
    }

    @Test
    @Transactional
    void loadBackfillsBlankPromptsOnExistingRow() {
        clearState();

        final LLMSettings existing = new LLMSettings();
        existing.setBaseUrl("http://localhost:11434");
        existing.setModelName("llama3.1");
        existing.setSystemPrompt("   ");
        existing.setToolSystemPrompt(null);
        existing.persistAndFlush();

        final LLMSettings loaded = modelConfigService.load();

        assertEquals(1L, LLMSettings.count());
        assertEquals(LLMSettings.DEFAULT_SYSTEM_PROMPT, loaded.getSystemPrompt());
        assertEquals(LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT, loaded.getToolSystemPrompt());
    }

    @Test
    @Transactional
    void saveMergesSettingsAndAppliesTrimmedRuntimeOverrides() {
        clearState();

        final LLMSettings existing = new LLMSettings();
        existing.setBaseUrl("http://old-host:11434");
        existing.setModelName("old-model");
        existing.setSystemPrompt(LLMSettings.DEFAULT_SYSTEM_PROMPT);
        existing.setToolSystemPrompt(LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
        existing.persistAndFlush();

        final LLMSettings updated = new LLMSettings();
        updated.id = existing.id;
        updated.setBaseUrl("  http://new-host:11434  ");
        updated.setModelName("  new-model  ");
        updated.setSystemPrompt("custom");
        updated.setToolSystemPrompt("custom-tool");

        modelConfigService.save(updated);

        final LLMSettings reloaded = LLMSettings.findById(existing.id);
        assertNotNull(reloaded);
        assertEquals("http://new-host:11434", reloaded.getBaseUrl().trim());
        assertEquals("new-model", reloaded.getModelName().trim());
        assertEquals("http://new-host:11434", System.getProperty(OLLAMA_BASE_URL_PROPERTY));
        assertEquals("new-model", System.getProperty(OLLAMA_MODEL_ID_PROPERTY));
    }

    private void clearState() {
        LLMSettings.deleteAll();
        System.clearProperty(OLLAMA_BASE_URL_PROPERTY);
        System.clearProperty(OLLAMA_MODEL_ID_PROPERTY);
    }
}