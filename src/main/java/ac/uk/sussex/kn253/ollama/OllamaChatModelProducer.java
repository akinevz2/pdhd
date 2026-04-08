package ac.uk.sussex.kn253.ollama;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import ac.uk.sussex.kn253.services.ModelConfigService;
import ac.uk.sussex.kn253.services.OllamaRuntimeEndpointService;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Produces the OllamaChatModel bean with structured output support enabled.
 * This allows AI Services to return structured JSON responses with guaranteed
 * schema compliance.
 */
@ApplicationScoped
public class OllamaChatModelProducer {

        @Inject
        OllamaConfig config;

        @Inject
        ModelConfigService modelConfigService;

        @Inject
        OllamaRuntimeEndpointService runtimeEndpointService;

        /**
         * Produces the primary ChatModel bean used by @RegisterAiService.
         * Enables RESPONSE_FORMAT_JSON_SCHEMA capability so that return types with
         * 
         * @Description annotations automatically generate JSON schema, and Ollama
         *              constrains responses to match that schema.
         */
        @Produces
        @ApplicationScoped
        public ChatModel produceChatModel() {
                final var settings = modelConfigService.load();
                final String baseUrl = runtimeEndpointService.resolvePersistedOrActive(settings.getBaseUrl());
                final String modelName = settings.getModelName() == null || settings.getModelName().isBlank()
                                ? config.modelName()
                                : settings.getModelName();

                return OllamaChatModel.builder()
                                .baseUrl(baseUrl)
                                .modelName(modelName)
                                .httpClientBuilder(new JdkHttpClientBuilder())
                                .temperature(config.temperature())
                                .numPredict(config.numPredict())
                                .numCtx(config.numCtx())
                                .timeout(java.time.Duration.ofSeconds(config.timeoutSeconds()))
                                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                                .logRequests(false)
                                .logResponses(false)
                                .build();
        }
}
