package ac.uk.sussex.kn253.repository;

import java.time.Instant;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "structured_summary", uniqueConstraints = @UniqueConstraint(columnNames = {
        "project_id", "summary_type", "target_path" }))
public class StructuredSummary extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectFolder project;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_type", nullable = false, length = 20)
    private SummaryType summaryType;

    @Column(name = "target_path", nullable = false, length = 512)
    private String targetPath;

    @Column(name = "purpose", nullable = false, columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "key_components_json", nullable = false, columnDefinition = "TEXT")
    private String keyComponentsJson;

    @Column(name = "dependencies_json", nullable = false, columnDefinition = "TEXT")
    private String dependenciesJson;

    @Column(name = "knowledge_ref_key", length = 200)
    private String knowledgeRefKey;

    @Column(name = "embedding_vector", columnDefinition = "TEXT")
    private String embeddingVector;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectFolder getProject() {
        return project;
    }

    public void setProject(final ProjectFolder project) {
        this.project = project;
    }

    public SummaryType getSummaryType() {
        return summaryType;
    }

    public void setSummaryType(final SummaryType summaryType) {
        this.summaryType = summaryType;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(final String targetPath) {
        this.targetPath = targetPath;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(final String purpose) {
        this.purpose = purpose;
    }

    public String getKeyComponentsJson() {
        return keyComponentsJson;
    }

    public void setKeyComponentsJson(final String keyComponentsJson) {
        this.keyComponentsJson = keyComponentsJson;
    }

    public String getDependenciesJson() {
        return dependenciesJson;
    }

    public void setDependenciesJson(final String dependenciesJson) {
        this.dependenciesJson = dependenciesJson;
    }

    public String getKnowledgeRefKey() {
        return knowledgeRefKey;
    }

    public void setKnowledgeRefKey(final String knowledgeRefKey) {
        this.knowledgeRefKey = knowledgeRefKey;
    }

    public String getEmbeddingVector() {
        return embeddingVector;
    }

    public void setEmbeddingVector(final String embeddingVector) {
        this.embeddingVector = embeddingVector;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(final String contentHash) {
        this.contentHash = contentHash;
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

    public static StructuredSummary findByProjectTypeAndPath(final ProjectFolder project, final SummaryType summaryType,
            final String targetPath) {
        return find("project = ?1 and summaryType = ?2 and targetPath = ?3", project, summaryType, targetPath)
                .firstResult();
    }

    public static List<StructuredSummary> listByProjectAndType(final ProjectFolder project,
            final SummaryType summaryType) {
        return find("project = ?1 and summaryType = ?2 order by updatedAt desc", project, summaryType).list();
    }
}
