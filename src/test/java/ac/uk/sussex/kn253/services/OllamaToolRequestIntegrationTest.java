package ac.uk.sussex.kn253.services;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.*;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
import ac.uk.sussex.kn253.tools.FiletypeTools;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class OllamaToolRequestIntegrationTest {

    private static final String REQUIRED_HOST = "desktop-box26.local";
    private static final String LIVE_MODEL_OVERRIDE_PROPERTY = "ollama.live.model";

    interface LiveToolAssistant {
        @SystemMessage("Determine whether the given file requires {{support}}. Respond with JSON containing 'required' (boolean) and 'reasoning' (string).")
        SupportResponse requiresSupport(@UserMessage String fileName, @V("support") String supportType);

        @SystemMessage("Return only the lowercase programming language identifier suitable for syntax highlighting (e.g. 'typescript', 'python', 'java', 'yaml'). Return 'text' if unknown. No explanation.")
        String getFileType(@UserMessage String fileName);
    }

    @Inject
    OllamaManagementService ollamaManagementService;

    @Inject
    OllamaConfig ollamaConfig;

    private LiveToolAssistant assistant;

    @BeforeEach
    void requireLiveOllamaHostAndModel() {
        final String baseUrl = ollamaConfig.baseUrl();

        Assumptions.assumeTrue(isRequiredHost(baseUrl),
                () -> "Live workstation tests require host " + REQUIRED_HOST + " but got " + baseUrl);
        Assumptions.assumeTrue(ollamaManagementService.isHealthy(baseUrl),
                () -> "Ollama is unreachable at " + baseUrl);

        final List<OllamaModelInfo> models = ollamaManagementService.listModels(baseUrl);
        Assumptions.assumeTrue(!models.isEmpty(),
                () -> "No models are available on live Ollama host: " + baseUrl);

        final String modelName = selectLiveModelName(models, ollamaConfig.modelName());

        assistant = AiServices.builder(LiveToolAssistant.class)
                .chatModel(OllamaChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(modelName)
                        .httpClientBuilder(new JdkHttpClientBuilder())
                        .timeout(Duration.ofSeconds(60))
                        .temperature(0.0)
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .logRequests(false)
                        .logResponses(false)
                        .build())
                .tools(new FiletypeTools())
                .build();
    }

    @Test
    void requiresSupportReturnsStructuredSupportResponse() {
        final SupportResponse response = assistant.requiresSupport(
                "README.md",
                "Markdown viewing support");

        assertNotNull(response, "Support response should not be null");
        assertNotNull(response.reasoning(), "Reasoning should not be null");
        assertFalse(response.reasoning().isBlank(), "Reasoning should not be blank");
    }

    @Test
    void getFileTypeReturnsLowercaseIdentifier() {
        final String response = assistant.getFileType("Example.java");

        assertNotNull(response, "Filetype response should not be null");
        assertFalse(response.isBlank(), "Filetype response should not be blank");
        assertTrue(response.matches("^[a-z0-9_-]+$"),
                () -> "Expected lowercase language identifier but got: " + response);
    }

    private static String selectLiveModelName(final List<OllamaModelInfo> models, final String configuredModelName) {
        final String overrideModel = System.getProperty(LIVE_MODEL_OVERRIDE_PROPERTY);

        if (overrideModel != null && !overrideModel.isBlank()) {
            final Optional<String> match = findModel(models, overrideModel);
            Assumptions.assumeTrue(match.isPresent(),
                    () -> "Override model is not available on live host: " + overrideModel);
            return match.get();
        }

        final Optional<String> configuredMatch = findModel(models, configuredModelName);
        if (configuredMatch.isPresent()) {
            return configuredMatch.get();
        }

        return models.stream()
                .map(OllamaModelInfo::getName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Live host returned models without usable names"));
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

    private static boolean isRequiredHost(final String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        try {
            final URI uri = URI.create(baseUrl);
            final String host = uri.getHost();
            return REQUIRED_HOST.equalsIgnoreCase(host);
        } catch (final Exception e) {
            return false;
        }
    }
}