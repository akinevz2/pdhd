package ac.uk.sussex.kn253.services.ai;

import java.util.ArrayList;
import java.util.List;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Owns domain knowledge about embeddings and delegates vector generation
 * to the injected {@link EmbeddingModel}.
 *
 * <p>
 * Lifecycle of the underlying model is managed by CDI via
 * {@link ac.uk.sussex.kn253.ollama.OllamaChatModelProducer}.
 */
@ApplicationScoped
public class EmbeddingsService {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    OllamaConfig ollamaConfig;

    /**
     * Embeds the supplied text and returns its vector representation.
     *
     * @param text the text to embed; must not be null
     * @return the embedding vector, or an empty list when embeddings are disabled
     */
    public List<Float> embed(final String text) {
        if (!ollamaConfig.embeddingEnabled().booleanValue()) {
            return List.of();
        }
        final TextSegment segment = TextSegment.from(text);
        final Response<Embedding> response = embeddingModel.embed(segment);
        final float[] vector = response.content().vector();
        final List<Float> result = new ArrayList<>(vector.length);
        for (final float value : vector) {
            result.add(value);
        }
        return result;
    }

    /**
     * Returns whether embedding generation is enabled per configuration.
     */
    public boolean isEmbeddingEnabled() {
        return ollamaConfig.embeddingEnabled().booleanValue();
    }

    /**
     * Returns the configured maximum number of retrieval results.
     */
    public int maxResults() {
        return ollamaConfig.embeddingMaxResults().intValue();
    }

    /**
     * Returns the configured nominal embedding vector dimension.
     */
    public int embeddingDimension() {
        return ollamaConfig.embeddingDimension().intValue();
    }
}
