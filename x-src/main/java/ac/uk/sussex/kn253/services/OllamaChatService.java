package ac.uk.sussex.kn253.services;

import java.time.Duration;

import ac.uk.sussex.kn253.entities.LLMSettings;
import ac.uk.sussex.kn253.model.ollama.OllamaConfig;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Production {@link IChatService} implementation backed by Ollama streaming
 * chat and embedding models built from the typed {@link OllamaConfig}.
 */
@ApplicationScoped
public class OllamaChatService implements IChatService {

    @Inject
    OllamaConfig ollamaConfig;

    private StreamingChatModel streamingChatModel;
    private EmbeddingModel ollamaEmbeddingModel;

    @PostConstruct
    void init() {
        streamingChatModel = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaConfig.baseUrl())
                .modelName(ollamaConfig.modelName())
                .temperature(ollamaConfig.temperature())
                .httpClientBuilder(new JdkHttpClientBuilder())
                .timeout(Duration.ofSeconds(ollamaConfig.timeoutSeconds()))
                .build();

        if (Boolean.TRUE.equals(ollamaConfig.embeddingEnabled())) {
            ollamaEmbeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(ollamaConfig.baseUrl())
                    .modelName(ollamaConfig.embeddingModelName())
                    .httpClientBuilder(new JdkHttpClientBuilder())
                    .timeout(Duration.ofSeconds(ollamaConfig.timeoutSeconds()))
                    .build();
        }
    }

    @Override
    public StreamingChatModel model() {
        return streamingChatModel;
    }

    @Override
    public EmbeddingModel embeddingModel() {
        return ollamaEmbeddingModel;
    }

    /**
     * Rebuilds the streaming chat model (and embedding model when enabled) from
     * the supplied settings so that subsequent calls use the updated endpoint
     * and model name without restarting the application.
     *
     * <p>
     * This method is also invoked by the CDI observer {@link #onSettingsChanged}
     * whenever a {@link LLMSettings} event is fired by {@link ModelConfigService},
     * wiring the full save to reload path automatically.
     *
     * @param settings the new settings to apply; {@code baseUrl} and
     *                 {@code modelName} fall back to the typed config defaults
     *                 when blank or {@code null}
     */
    public void reload(final LLMSettings settings) {
        final String baseUrl = (settings.getBaseUrl() != null && !settings.getBaseUrl().isBlank())
                ? settings.getBaseUrl()
                : ollamaConfig.baseUrl();
        final String modelName = (settings.getModelName() != null && !settings.getModelName().isBlank())
                ? settings.getModelName()
                : ollamaConfig.modelName();

        streamingChatModel = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(ollamaConfig.temperature())
                .httpClientBuilder(new JdkHttpClientBuilder())
                .timeout(Duration.ofSeconds(ollamaConfig.timeoutSeconds()))
                .build();

        // null → use config default; "" → disabled; non-blank → use persisted value
        final String settingsEmbName = settings.getEmbeddingModelName();
        final String embeddingModelName;
        final boolean embEnabled;
        if (settingsEmbName == null) {
            embeddingModelName = ollamaConfig.embeddingModelName();
            embEnabled = Boolean.TRUE.equals(ollamaConfig.embeddingEnabled());
        } else if (settingsEmbName.isBlank()) {
            embeddingModelName = "";
            embEnabled = false;
        } else {
            embeddingModelName = settingsEmbName;
            embEnabled = Boolean.TRUE.equals(ollamaConfig.embeddingEnabled());
        }

        if (embEnabled && !embeddingModelName.isBlank()) {
            ollamaEmbeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(embeddingModelName)
                    .httpClientBuilder(new JdkHttpClientBuilder())
                    .timeout(Duration.ofSeconds(ollamaConfig.timeoutSeconds()))
                    .build();
        } else {
            ollamaEmbeddingModel = null;
        }
    }

    /**
     * CDI observer that delegates to {@link #reload} whenever a
     * {@link LLMSettings} event is fired (e.g. from
     * {@link ModelConfigService#save}).
     */
    void onSettingsChanged(@Observes final LLMSettings settings) {
        reload(settings);
    }

    @Override
    public Multi<String> streamResponse(final String input) {
        return Multi.createFrom().emitter(emitter -> {
            try {
                streamingChatModel.chat(input, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(final String token) {
                        emitter.emit(token);
                    }

                    @Override
                    public void onCompleteResponse(final ChatResponse chatResponse) {
                        emitter.complete();
                    }

                    @Override
                    public void onError(final Throwable error) {
                        emitter.fail(error);
                    }
                });
            } catch (final Exception e) {
                emitter.fail(e);
            }
        });
    }
}
