package ac.uk.sussex.kn253.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * A node in the embedded-token vector graph used to augment conversation
 * history with structured metadata.
 *
 * <p>
 * Each node owns two parallel indexed slot lists:
 * <ul>
 *   <li>{@code conversationEntries} — ordered text fragments taken from the
 *       conversation history (one entry per slot index).</li>
 *   <li>{@code metadataEntries} — parallel JSON-encoded metadata blobs for
 *       each conversation entry at the same index.</li>
 * </ul>
 * Relationships to other nodes determine which augmentations are passed as
 * metadata to the conversation history embedding at retrieval time.
 *
 * <p>
 * All vector data follows the same TEXT-column serialisation convention as
 * {@link ProjectKnowledge}: embedding vectors are stored as JSON-serialised
 * {@code float[]} strings.
 */
@Entity
@Table(name = "embedded_token_vector_node")
public class EmbeddedTokenVectorNode extends PanacheEntityBase {

    private static final String TABLE_CONVERSATION = "etvn_conversation_entries";
    private static final String TABLE_METADATA = "etvn_metadata_entries";
    private static final String TABLE_RELATIONSHIPS = "etvn_relationships";
    private static final String COL_NODE_ID = "node_id";
    private static final String COL_SOURCE_NODE_ID = "source_node_id";
    private static final String COL_TARGET_NODE_ID = "target_node_id";
    private static final String COL_ENTRY_INDEX = "entry_index";
    private static final String COL_ENTRY_TEXT = "entry_text";
    private static final String COL_METADATA_JSON = "metadata_json";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * JSON-serialised {@code float[]} embedding vector for this node.
     * Null until the node has been indexed.
     */
    @Column(name = "embedding_vector", columnDefinition = "TEXT")
    private String embeddingVector;

    /**
     * Ordered conversation-entry slots. Each element is a text fragment from
     * the conversation history; the list index is the slot index.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = TABLE_CONVERSATION,
            joinColumns = @JoinColumn(name = COL_NODE_ID))
    @OrderColumn(name = COL_ENTRY_INDEX)
    @Column(name = COL_ENTRY_TEXT, columnDefinition = "TEXT", nullable = false)
    private List<String> conversationEntries = new ArrayList<>();

    /**
     * Parallel metadata slots.  Each element is a JSON-encoded metadata blob
     * for the conversation entry at the same slot index.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = TABLE_METADATA,
            joinColumns = @JoinColumn(name = COL_NODE_ID))
    @OrderColumn(name = COL_ENTRY_INDEX)
    @Column(name = COL_METADATA_JSON, columnDefinition = "TEXT", nullable = false)
    private List<String> metadataEntries = new ArrayList<>();

    /**
     * Directed relationships to other nodes.  The embedding vectors of related
     * nodes are passed as augmentation metadata to the conversation history
     * vector at retrieval time.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = TABLE_RELATIONSHIPS,
            joinColumns = @JoinColumn(name = COL_SOURCE_NODE_ID),
            inverseJoinColumns = @JoinColumn(name = COL_TARGET_NODE_ID))
    private List<EmbeddedTokenVectorNode> relatedNodes = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EmbeddedTokenVectorNode() {
    }

    public String getEmbeddingVector() {
        return embeddingVector;
    }

    public void setEmbeddingVector(final String embeddingVector) {
        this.embeddingVector = embeddingVector;
    }

    public List<String> getConversationEntries() {
        return conversationEntries;
    }

    public void setConversationEntries(final List<String> conversationEntries) {
        this.conversationEntries = conversationEntries;
    }

    public List<String> getMetadataEntries() {
        return metadataEntries;
    }

    public void setMetadataEntries(final List<String> metadataEntries) {
        this.metadataEntries = metadataEntries;
    }

    public List<EmbeddedTokenVectorNode> getRelatedNodes() {
        return relatedNodes;
    }

    public void setRelatedNodes(final List<EmbeddedTokenVectorNode> relatedNodes) {
        this.relatedNodes = relatedNodes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
