package ac.uk.sussex.kn253.model;

/**
 * Represents a matched embedding from a semantic search, including similarity
 * score.
 */
public record EmbeddingMatch(
        String id,
        String text,
        String sourceType,
        String sourceId,
        long timestamp, float similarity) {

    public EmbeddingMatch(final EmbeddingEntity entity, final float similarity) {
        this(
                entity.getId(),
                entity.getTextSnippet(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getTimestamp(),
                similarity);
    }

    public EmbeddingMatch(final EmbeddingEntity entity) {
        this(entity, 0f);
    }
}
