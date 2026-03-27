package ac.uk.sussex.kn253.model;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

/**
 * Stores a keyed JSON knowledge blob for a {@link Project}.
 *
 * <p>
 * The assistant can read and write these records to accumulate structured
 * knowledge about a project across iterative queries. Each
 * {@code (project, key)} pair is unique; upserting replaces the previous value.
 */
@Entity
@Table(name = "project_knowledge", uniqueConstraints = @UniqueConstraint(columnNames = { "project_id",
        "knowledge_key" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectKnowledge extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

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
    // Factory / query helpers
    // -------------------------------------------------------------------------

    /**
     * Finds an existing entry by project and key.
     *
     * @param project the owning project.
     * @param key     the knowledge key.
     * @return the matching entity, or {@code null}.
     */
    public static ProjectKnowledge findByProjectAndKey(final Project project, final String key) {
        return find("project = ?1 and key = ?2", project, key).firstResult();
    }
}
