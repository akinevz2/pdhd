package ac.uk.sussex.kn253.model;

import jakarta.persistence.*;

/**
 * Persistent storage for embedding vectors.
 * 
 * <p>
 * Each embedding is stored with its vector representation, source metadata,
 * and a snippet of the original text for retrieval and display.
 */
@Entity
@Table(name = "embeddings")
public class EmbeddingEntity {

    @Id
    @Column(name = "id", nullable = false, length = 255)
    private String id;

    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Lob
    @Column(name = "vector_data", nullable = false)
    private byte[] vectorData; // Serialized float array

    @Column(name = "text_snippet", columnDefinition = "TEXT")
    private String textSnippet;

    @Column(name = "full_text", columnDefinition = "TEXT")
    private String fullText;

    @Column(name = "source_type", length = 50)
    private String sourceType; // "user_input", "file_content", "tool_output"

    @Column(name = "source_id", length = 255)
    private String sourceId;

    @Column(name = "timestamp", nullable = false)
    private long timestamp;

    @Column(name = "memory_id", length = 255)
    private String memoryId;

    @Column(name = "embedding_dimension")
    private int dimension;

    // Constructors

    public EmbeddingEntity() {
    }

    public EmbeddingEntity(
            final String id,
            final String sessionId,
            final byte[] vectorData,
            final String textSnippet,
            final String fullText,
            final String sourceType,
            final String sourceId,
            final long timestamp,
            final String memoryId,
            final int dimension) {
        this.id = id;
        this.sessionId = sessionId;
        this.vectorData = vectorData;
        this.textSnippet = textSnippet;
        this.fullText = fullText;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.timestamp = timestamp;
        this.memoryId = memoryId;
        this.dimension = dimension;
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public byte[] getVectorData() {
        return vectorData;
    }

    public void setVectorData(final byte[] vectorData) {
        this.vectorData = vectorData;
    }

    public String getTextSnippet() {
        return textSnippet;
    }

    public void setTextSnippet(final String textSnippet) {
        this.textSnippet = textSnippet;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(final String fullText) {
        this.fullText = fullText;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(final String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(final String sourceId) {
        this.sourceId = sourceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(final String memoryId) {
        this.memoryId = memoryId;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(final int dimension) {
        this.dimension = dimension;
    }
}
