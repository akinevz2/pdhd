package ac.uk.sussex.kn253.services;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.*;

import org.junit.jupiter.api.*;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class OllamaWorkstationIntegrationTest {

    private static final String LIVE_MODEL_OVERRIDE_PROPERTY = "ollama.live.model";

    interface LiveChatAssistant {
        @UserMessage("{{it}}")
        String chat(String userMessage);
    }

    @Inject
    OllamaManagementService ollamaManagementService;

    @Inject
    OllamaConfig ollamaConfig;

    private LiveChatAssistant assistant;
    private String selectedModelName;

    @BeforeEach
    void requireLiveOllamaHostAndModel() {
        final String baseUrl = ollamaConfig.baseUrl().orElse("");

        Assumptions.assumeTrue(!baseUrl.isBlank(),
            "Live workstation tests require pdhd.ollama.base-url to be configured");
        Assumptions.assumeTrue(ollamaManagementService.isHealthy(baseUrl),
                () -> "Ollama is unreachable at " + baseUrl);

        final List<OllamaModelInfo> models = ollamaManagementService.listModels(baseUrl);
        Assumptions.assumeTrue(!models.isEmpty(),
                () -> "No models are available on live Ollama host: " + baseUrl);

        selectedModelName = selectLiveModelName(models, ollamaConfig.modelName());
        assistant = AiServices.builder(LiveChatAssistant.class)
                .chatModel(OllamaChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(selectedModelName)
                        .httpClientBuilder(new JdkHttpClientBuilder())
                        .timeout(Duration.ofSeconds(60))
                        .temperature(0.0)
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .logRequests(false)
                        .logResponses(false)
                        .build())
                .build();
    }

    @Test
    void chatReturnsNonEmptyResponseFromLiveModel() {
        final String prompt = "Reply with exactly one short sentence that contains the word integration.";
        final String response = assistant.chat(prompt);

        assertNotNull(response, "Model response should not be null");
        assertFalse(response.isBlank(), "Model response should not be blank");
        assertTrue(response.toLowerCase(Locale.ROOT).contains("integration"),
                () -> "Expected response to include 'integration' but got: " + response + " [model="
                        + selectedModelName + "]");
    }

    private static String selectLiveModelName(final List<OllamaModelInfo> models, final String configuredModelName) {
        final String overrideModel = System.getProperty(LIVE_MODEL_OVERRIDE_PROPERTY);

        if (overrideModel != null && !overrideModel.isBlank()) {
            final Optional<String> match = findModel(models, overrideModel);
            assertTrue(match.isPresent(),
                    () -> "Override model is not available on live host: " + overrideModel
                            + "; available models=" + availableModelNames(models));
            return match.get();
        }

        final Optional<String> configuredMatch = findModel(models, configuredModelName);
        assertTrue(configuredMatch.isPresent(),
                () -> "Configured model from pdhd.ollama.model-name is not available on live host: "
                        + configuredModelName + "; available models=" + availableModelNames(models));
        return configuredMatch.get();
    }

    private static Optional<String> findModel(final List<OllamaModelInfo> models, final String expected) {
        if (expected == null || expected.isBlank()) {
            return Optional.empty();
        }
        return models.stream()
                .filter(model -> expected.equals(model.getName()) || expected.equals(model.getModel()))
                .map(OllamaModelInfo::getName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
    }

    private static List<String> availableModelNames(final List<OllamaModelInfo> models) {
        return models.stream()
                .map(OllamaModelInfo::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

}