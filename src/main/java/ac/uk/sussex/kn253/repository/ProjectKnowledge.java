package ac.uk.sussex.kn253.repository;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * Stores a keyed JSON knowledge blob for a {@link ProjectFolder}.
 *
 * <p>
 * The assistant can read and write these records to accumulate structured
 * knowledge about a project across iterative queries. Each
 * {@code (project, key)} pair is unique; upserting replaces the previous value.
 */
@Entity
@Table(name = "project_knowledge", uniqueConstraints = @UniqueConstraint(columnNames = { "project_id",
        "knowledge_key" }))
public class ProjectKnowledge extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectFolder project;

    /**
     * Logical label for this knowledge entry, e.g. {@code "summary"} or
     * {@code "todos"}.
     */
    @Column(name = "knowledge_key", nullable = false, length = 200)
    private String key;

    /** Arbitrary JSON content – structure is defined by the caller. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String jsonContent;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    /**
     * JSON-serialised {@code float[]} embedding vector for this entry.
     * Null until the entry has been indexed by the RAG service.
     */
    @Column(name = "embedding_vector", columnDefinition = "TEXT")
    private String embeddingVector;

    public ProjectKnowledge() {
    }

    public ProjectKnowledge(final Long id, final ProjectFolder project, final String key, final String jsonContent,
            final Instant createdAt, final Instant updatedAt, final String embeddingVector) {
        this.id = id;
        this.project = project;
        this.key = key;
        this.jsonContent = jsonContent;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.embeddingVector = embeddingVector;
    }

    // -------------------------------------------------------------------------
    // Dependent embeddable
    // -------------------------------------------------------------------------

    /**
     * A single dependent attached to a {@link ProjectKnowledge} record.
     *
     * <p>
     * Carries a raw token byte-vector (stored as a JSON-serialised {@code byte[]}
     * in the {@code token_vector} column) and a cached embedding float-vector
     * (stored as a JSON-serialised {@code float[]} in the {@code cached_embedding}
     * column).  Both columns are TEXT to remain consistent with how
     * {@link ProjectKnowledge#embeddingVector} is persisted.
     */
    @Embeddable
    public static class Dependent {

        @Column(name = "token_vector", columnDefinition = "TEXT")
        private String tokenVector;

        @Column(name = "cached_embedding", columnDefinition = "TEXT")
        private String cachedEmbedding;

        public Dependent() {
        }

        public Dependent(final String tokenVector, final String cachedEmbedding) {
            this.tokenVector = tokenVector;
            this.cachedEmbedding = cachedEmbedding;
        }

        public String getTokenVector() {
            return tokenVector;
        }

        public void setTokenVector(final String tokenVector) {
            this.tokenVector = tokenVector;
        }

        public String getCachedEmbedding() {
            return cachedEmbedding;
        }

        public void setCachedEmbedding(final String cachedEmbedding) {
            this.cachedEmbedding = cachedEmbedding;
        }
    }

    // -------------------------------------------------------------------------
    // Dependents map (byte-vector + cached Embedding keyed by DependentKey)
    // -------------------------------------------------------------------------

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "project_knowledge_dependents",
            joinColumns = @JoinColumn(name = "knowledge_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "dependent_key", length = 20)
    private Map<DependentKey, Dependent> dependents = new EnumMap<>(DependentKey.class);

    public Map<DependentKey, Dependent> getDependents() {
        return dependents;
    }

    public void setDependents(final Map<DependentKey, Dependent> dependents) {
        this.dependents = dependents;
    }

    public ProjectFolder getProject() {
        return project;
    }

    public void setProject(final ProjectFolder project) {
        this.project = project;
    }

    public String getKey() {
        return key;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    public String getJsonContent() {
        return jsonContent;
    }

    public void setJsonContent(final String jsonContent) {
        this.jsonContent = jsonContent;
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

    public String getEmbeddingVector() {
        return embeddingVector;
    }

    public void setEmbeddingVector(final String embeddingVector) {
        this.embeddingVector = embeddingVector;
    }

    // -------------------------------------------------------------------------
    // Factory / query helpers
    // -------------------------------------------------------------------------

    /**
     * Finds an existing entry by project and key.
     *
     * @param project the owning project.
     * @param key     the knowledge key.
     * @return the matching entity, or {@code null}.
     */
    public static ProjectKnowledge findByProjectAndKey(final ProjectFolder project, final String key) {
        return find("project = ?1 and key = ?2", project, key).firstResult();
    }
}
