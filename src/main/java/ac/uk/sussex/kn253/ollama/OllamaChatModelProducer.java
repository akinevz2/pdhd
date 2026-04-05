package ac.uk.sussex.kn253.ollama;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

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
        return OllamaChatModel.builder()
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
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
