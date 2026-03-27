package ac.uk.sussex.kn253.ollama;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.*;

import ac.uk.sussex.kn253.testsupport.OllamaTestSupport;

@Tag("ollama-workstation")
class OllamaWorkstationIntegrationTest {

    @Test
    void canReachWorkstationAndGenerateOneShotResponse() {
        final String baseUrl = OllamaTestSupport.testBaseUrl();
        Assumptions.assumeTrue(
                OllamaTestSupport.isReachable(baseUrl),
                () -> "Skipping: workstation Ollama not reachable at " + baseUrl);

        final List<String> models = OllamaTestSupport.modelNames(baseUrl);
        Assumptions.assumeTrue(!models.isEmpty(), "Skipping: no models available on workstation Ollama");

        final String model = models.get(0);
        final OllamaChatSession session = OllamaChatSession.builder()
                .baseUrl(baseUrl)
                .model(model)
                .build();

        final String response = session.sendOneShot("Reply with \"pong\".");
        assertNotNull(response);
        assertTrue(response.contains("pong"));
    }
}
