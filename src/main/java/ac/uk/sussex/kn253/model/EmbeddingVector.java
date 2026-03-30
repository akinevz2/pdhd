package ac.uk.sussex.kn253.model;

/**
 * Represents a single embedding vector for semantic search.
 * 
 * <p>
 * An embedding is a numerical representation of text that captures
 * semantic meaning, enabling similarity-based retrieval.
 */
public class EmbeddingVector {

    private final String id;
    private final float[] vector;
    private final String text;
    private final long timestamp;
    private final String sourceType;
    private final String sourceId;
    private final String memoryId;

    public EmbeddingVector(
            final String id,
            final float[] vector,
            final String text,
            final long timestamp,
            final String sourceType,
            final String sourceId,
            final String memoryId) {
        this.id = id;
        this.vector = vector;
        this.text = text;
        this.timestamp = timestamp;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.memoryId = memoryId;
    }

    public String id() {
        return id;
    }

    public float[] vector() {
        return vector;
    }

    public String text() {
        return text;
    }

    public long timestamp() {
        return timestamp;
    }

    public String sourceType() {
        return sourceType;
    }

    public String sourceId() {
        return sourceId;
    }

    public String memoryId() {
        return memoryId;
    }

    public int dimension() {
        return vector != null ? vector.length : 0;
    }

    /**
     * Compute cosine similarity with another embedding.
     * Returns value between -1 and 1 (typically 0 to 1 for normalized vectors).
     */
    public float cosineSimilarity(final EmbeddingVector other) {
        if (other == null || other.vector.length != this.vector.length) {
            return 0f;
        }

        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;

        for (int i = 0; i < this.vector.length; i++) {
            dotProduct += this.vector[i] * other.vector[i];
            normA += this.vector[i] * this.vector[i];
            normB += other.vector[i] * other.vector[i];
        }

        if (normA == 0f || normB == 0f) {
            return 0f;
        }

        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}
