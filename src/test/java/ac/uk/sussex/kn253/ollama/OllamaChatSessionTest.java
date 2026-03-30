package ac.uk.sussex.kn253.ollama;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.testsupport.OllamaTestSupport;

class OllamaChatSessionTest {

    @Test
    void buildEffectiveSystemPromptIncludesPerRequestMetadata() {
        final String baseUrl = OllamaTestSupport.testBaseUrl();
        Assumptions.assumeTrue(
                OllamaTestSupport.isReachable(baseUrl),
                () -> "Skipping: workstation Ollama not reachable at " + baseUrl);

        final OllamaChatSession session = new OllamaChatSession(baseUrl, "llama3.2")
                .setSystemPrompt("Base prompt")
            .setRequestMetadataSupplier(() -> "Current folder metadata:\n{\"hasHistory\":true}");

        final String effectivePrompt = session.buildEffectiveSystemPrompt();

        assertTrue(effectivePrompt.contains("Base prompt"));
        assertTrue(effectivePrompt.contains("Current folder metadata:"));
        assertTrue(effectivePrompt.contains("\"hasHistory\":true"));
    }

}