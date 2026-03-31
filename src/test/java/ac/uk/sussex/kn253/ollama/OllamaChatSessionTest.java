package ac.uk.sussex.kn253.ollama;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.testsupport.OllamaTestSupport;

class OllamaChatSessionTest {

    private static final Logger LOG = Logger.getLogger(OllamaChatSessionTest.class);

    @Test
    void buildEffectiveSystemPromptIncludesCwdFromContextSupplier() {
        final String baseUrl = OllamaTestSupport.testBaseUrl();
        LOG.infof("Using Ollama baseUrl in test: %s", baseUrl);
        Assumptions.assumeTrue(
                OllamaTestSupport.isReachable(baseUrl),
                () -> "Skipping: workstation Ollama not reachable at " + baseUrl);

        final OllamaChatSession session = new OllamaChatSession(baseUrl, "llama3.1:8b-instruct-q4_K_M")
                .setSystemPrompt("Base prompt")
                .setContextSupplier(() -> new MacroContext("/workspace/demo"));

        final String effectivePrompt = session.buildEffectiveSystemPrompt();

        assertTrue(effectivePrompt.contains("Base prompt"));
        assertTrue(effectivePrompt.contains("Current working directory: /workspace/demo"));
    }

}