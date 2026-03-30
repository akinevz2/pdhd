package ac.uk.sussex.kn253.model;

/**
 * Represents a matched embedding from a semantic search, including similarity
 * score.
 */
public class EmbeddingMatch {

    private final String id;
    private final String text;
    private final float similarity;
    private final String sourceType;
    private final String sourceId;
    private final long timestamp;

    public EmbeddingMatch(
            final String id,
            final String text,
            final float similarity,
            final String sourceType,
            final String sourceId,
            final long timestamp) {
        this.id = id;
        this.text = text;
        this.similarity = similarity;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.timestamp = timestamp;
    }

    public String id() {
        return id;
    }

    public String text() {
        return text;
    }

    public float similarity() {
        return similarity;
    }

    public String sourceType() {
        return sourceType;
    }

    public String sourceId() {
        return sourceId;
    }

    public long timestamp() {
        return timestamp;
    }
}
