package ac.uk.sussex.kn253.ollama;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.*;

import ac.uk.sussex.kn253.testsupport.OllamaTestSupport;

@Tag("ollama-workstation")
class OllamaWorkstationIntegrationTest {

        private static final Logger LOG = Logger.getLogger(OllamaWorkstationIntegrationTest.class);

        @Test
        void canReachWorkstationAndGenerateOneShotResponse() {
                final String baseUrl = OllamaTestSupport.testBaseUrl();
                LOG.infof("Using Ollama baseUrl in test: %s", baseUrl);
                Assumptions.assumeTrue(
                                OllamaTestSupport.isReachable(baseUrl),
                                () -> "Skipping: workstation Ollama not reachable at " + baseUrl);

                final List<String> models = OllamaTestSupport.modelNames(baseUrl);
                Assumptions.assumeTrue(!models.isEmpty(), "Skipping: no models available on workstation Ollama");

                final String preferredModel = OllamaTestSupport.toolModelPreference();
                final String model = OllamaTestSupport.resolveAvailableModelName(models, preferredModel);
                Assumptions.assumeTrue(
                                model != null,
                                () -> "Skipping: preferred chat model not available: " + preferredModel
                                                + ". Available models: "
                                                + models);

                final OllamaChatSession session = OllamaChatSession.builder()
                                .baseUrl(baseUrl)
                                .model(model)
                                .build();

                final String response = session.sendOneShot("Reply with \"pong\".");
                assertNotNull(response);
                assertTrue(response.toLowerCase(Locale.ROOT).contains("pong"));
        }
}
