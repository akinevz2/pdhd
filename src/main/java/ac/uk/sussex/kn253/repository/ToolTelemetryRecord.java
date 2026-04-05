package ac.uk.sussex.kn253.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * Persistent record of a single tool invocation for long-term telemetry
 * retention.
 *
 * <p>
 * Each row captures the raw inputs to
 * {@link ac.uk.sussex.kn253.services.macro.ToolTelemetryService#record}
 * so that historical data survives application restarts. The table is managed
 * by
 * Hibernate's {@code update} strategy and is therefore never dropped.
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

    @Column(name = "argument_validation_failure", nullable = false)
    public boolean argumentValidationFailure;

    @Column(name = "recorded_at", nullable = false)
    public long recordedAt;
}
