package ac.uk.sussex.kn253.repository;

import ac.uk.sussex.kn253.model.ToolTelemetryRecord;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Writes raw telemetry events to the {@code tool_telemetry} table.
 *
 * <p>
 * Runs within the caller's transaction (or starts a new one if none is active)
 * so that the record is committed together with any surrounding work.
 */
@ApplicationScoped
public class ToolTelemetryRepository implements PanacheRepository<ToolTelemetryRecord> {

    @Transactional
    public void save(
            final String toolName,
            final String moduleName,
            final long durationNanos,
            final String errorClass,
            final boolean argumentValidationFailure) {
        final ToolTelemetryRecord record = new ToolTelemetryRecord();
        record.toolName = toolName;
        record.moduleName = moduleName;
        record.durationNanos = durationNanos;
        record.errorClass = errorClass;
        record.argumentValidationFailure = argumentValidationFailure;
        record.recordedAt = System.currentTimeMillis();
        persist(record);
    }
}
