package ac.uk.sussex.kn253.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * Persistent record of a single tool invocation for long-term telemetry
 * retention.
 *
 * <p>
 * Each row captures the raw inputs from every {@code @Tool}-annotated method
 * so that historical evidence survives application restarts.
 *
 * <p>
 * <strong>Operational constraint (recommendations §8):</strong> this table
 * ({@code tool_telemetry}) MUST NOT be dropped, truncated, or migrated
 * destructively. The Hibernate schema strategy in production is {@code update}
 * to enforce this. Any migration or refactor that would remove existing rows
 * must be reviewed and replaced with a safe, additive migration path before
 * merging.
 *
 * <p>
 * Schema evolution must be backward-compatible: add columns with defaults
 * rather than removing or renaming existing ones.
 */
@Entity
@Table(name = "tool_telemetry")
public class ToolTelemetryRecord extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tool_name", nullable = false)
    public String toolName;

    @Column(name = "module_name")
    public String moduleName;

    @Column(name = "duration_nanos", nullable = false)
    public long durationNanos;

    @Column(name = "error_class")
    public String errorClass;

    @Column(name = "input_payload", columnDefinition = "TEXT")
    public String inputPayload;

    @Column(name = "output_payload", columnDefinition = "TEXT")
    public String outputPayload;

    /**
     * Optional JSON payload produced by the tool alongside the human-readable
     * {@link #outputPayload}. Populated when a tool returns a typed result;
     * {@code null} for string-only tools. Supports evolution via
     * {@link #outputSchemaVersion}.
     */
    @Column(name = "typed_output_payload", columnDefinition = "TEXT")
    public String typedOutputPayload;

    /**
     * Schema version tag for {@link #typedOutputPayload}. A {@code null} or zero
     * value indicates that no typed payload was recorded.
     */
    @Column(name = "output_schema_version")
    public Integer outputSchemaVersion;

    @Column(name = "success", nullable = false)
    public boolean success;

    @Column(name = "argument_validation_failure", nullable = false)
    public boolean argumentValidationFailure;

    @Column(name = "recorded_at", nullable = false)
    public long recordedAt;
}
