package ac.uk.sussex.kn253.model;

/**
 * Represents a single embedding vector for semantic search.
 * 
 * <p>
 * An embedding is a numerical representation of text that captures
 * semantic meaning, enabling similarity-based retrieval.
 */
public record EmbeddingVector(
        String id,
        String text,
        long timestamp,
        String sourceType,
        String sourceId,
        String memoryId, float[] vector) {

    public EmbeddingVector(final EmbeddingEntity entity, final float[] vector) {
        this(
                entity.getId(),
                entity.getTextSnippet(),
                entity.getTimestamp(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getMemoryId(), vector);
    }

    public int dimension() {
        return vector != null ? vector.length : 0;
    }

    /**
     * Compute cosine similarity with another embedding.
     * Returns value between -1 and 1 (typically 0 to 1 for normalized vectors).
     */
    public float cosineSimilarity(final EmbeddingVector other) {
        if (other == null || other.vector().length != this.vector.length) {
            return 0f;
        }

        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;

        for (int i = 0; i < this.vector.length; i++) {
            dotProduct += this.vector[i] * other.vector()[i];
            normA += this.vector[i] * this.vector[i];
            normB += other.vector()[i] * other.vector()[i];
        }

        if (normA == 0f || normB == 0f) {
            return 0f;
        }

        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}
