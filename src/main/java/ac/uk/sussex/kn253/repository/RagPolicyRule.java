package ac.uk.sussex.kn253.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * DB-backed policy rules consumed by RAG and filesystem classification paths.
 */
@Entity
@Table(name = "rag_policy_rule", uniqueConstraints = @UniqueConstraint(columnNames = { "rule_kind", "matcher" }))
public class RagPolicyRule extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_kind", nullable = false, length = 80)
    private RagPolicyRuleKind kind;

    @Column(name = "matcher", nullable = false, length = 255)
    private String matcher;

    @Column(name = "mapped_value", length = 255)
    private String mappedValue;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public RagPolicyRuleKind getKind() {
        return kind;
    }

    public void setKind(final RagPolicyRuleKind kind) {
        this.kind = kind;
    }

    public String getMatcher() {
        return matcher;
    }

    public void setMatcher(final String matcher) {
        this.matcher = matcher;
    }

    public String getMappedValue() {
        return mappedValue;
    }

    public void setMappedValue(final String mappedValue) {
        this.mappedValue = mappedValue;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
