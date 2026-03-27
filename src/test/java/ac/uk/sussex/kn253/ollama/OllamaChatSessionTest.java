package ac.uk.sussex.kn253.ollama;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OllamaChatSessionTest {

    @Test
    void buildEffectiveSystemPromptIncludesPerRequestMetadata() {
        final OllamaChatSession session = new OllamaChatSession("http://localhost:11434", "llama3.2")
                .setSystemPrompt("Base prompt")
                .setRequestMetadataSupplier(() -> "Current folder metadata:\n- previouslyWorkedOnHere: true");

        final String effectivePrompt = session.buildEffectiveSystemPrompt();

        assertTrue(effectivePrompt.contains("Base prompt"));
        assertTrue(effectivePrompt.contains("Current folder metadata:"));
        assertTrue(effectivePrompt.contains("previouslyWorkedOnHere: true"));
    }
}