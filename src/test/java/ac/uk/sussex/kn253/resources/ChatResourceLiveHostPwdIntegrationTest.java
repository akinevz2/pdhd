package ac.uk.sussex.kn253.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
import ac.uk.sussex.kn253.services.ModelConfigService;
import ac.uk.sussex.kn253.services.OllamaManagementService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@Tag("live-host")
class ChatResourceLiveHostPwdIntegrationTest {

    private static final String DEFAULT_LIVE_HOST = "http://ws-cvn.local:11434";

    @Inject
    ChatResource chatResource;

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    OllamaManagementService ollamaManagementService;

    @Test
    void pwdPrompt_returnsAbsolutePath_notProxyOrPackageName() {
        final String liveHost = System.getProperty("pdhd.live.ollama.host", DEFAULT_LIVE_HOST).trim();

        // User requested live-host integration against ws-cvn, not ws-vision.
        assumeFalse(liveHost.contains("ws-vision"),
                "Live host must target ws-cvn while ws-vision is unavailable.");
        assumeTrue(ollamaManagementService.isHealthy(liveHost),
                "Live Ollama host is not reachable: " + liveHost);

        final List<OllamaModelInfo> models = ollamaManagementService.listModels(liveHost);
        assumeTrue(models != null && !models.isEmpty(), "No models available on live host: " + liveHost);

        final LLMSettings settings = modelConfigService.load();
        final String previousBaseUrl = settings.getBaseUrl();
        final String previousModelName = settings.getModelName();

        settings.setBaseUrl(liveHost);
        settings.setModelName(models.get(0).runtimeName());
        modelConfigService.save(settings);

        try {
            final ChatResource.ChatRequest request = new ChatResource.ChatRequest(
                    "Return only the absolute current working directory path as plain text.");

            final String response = String.join("",
                    chatResource.stream(request)
                            .collect().asList()
                            .await().atMost(Duration.ofSeconds(120)))
                    .trim();

            assertFalse(response.isBlank(), "Expected non-empty response from live-host chat run.");
            assertFalse(response.startsWith("Error"), response);

            // Regression guard: a proxy/package name is not a filesystem path.
            assertFalse(response.contains("_ClientProxy"), response);
            assertFalse(response.contains("$Proxy"), response);
            assertFalse(response.contains("ac.uk.sussex"), response);

            // Path sanity checks.
            assertTrue(response.startsWith("/"), "Expected absolute Unix path, got: " + response);
            assertFalse(response.contains("\n"), "Expected a single path line, got: " + response);
        } finally {
            settings.setBaseUrl(previousBaseUrl);
            settings.setModelName(previousModelName);
            modelConfigService.save(settings);
        }
    }
}
