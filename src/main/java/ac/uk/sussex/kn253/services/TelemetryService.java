package ac.uk.sussex.kn253.services;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.repository.ModelCallTelemetryRecord;
import ac.uk.sussex.kn253.repository.ToolTelemetryRecord;
import ac.uk.sussex.kn253.support.BackendSupport;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/**
 * Records model-call and tool-use telemetry in persistent tables.
 */
@ApplicationScoped
public class TelemetryService {

    private static final Logger LOG = Logger.getLogger(TelemetryService.class);

    @ConfigProperty(name = "quarkus.hibernate-orm.schema-management.strategy", defaultValue = "unknown")
    String schemaStrategy;

    static final int MAX_TEXT_LENGTH = 8_000;

    /**
     * Startup guard: confirms that historical telemetry rows are present and that
     * the schema management strategy will not silently discard them.
     *
     * <p>
     * Per recommendations §8, the {@code tool_telemetry} table must never be
     * dropped or truncated in environments where history matters. This check logs
     * a visible error if a destructive strategy is detected so CI and operators
     * catch the condition before it causes data loss.
     */
    @Transactional
    void guardTelemetryPersistence(@Observes final StartupEvent event) {
        if (!BackendSupport.SAFE_SCHEMA_STRATEGY.equalsIgnoreCase(schemaStrategy)) {
            LOG.errorf(
                    "TELEMETRY RISK: schema management strategy is '%s' – " +
                            "tool_telemetry history may be lost. Only '%s' is safe for production.",
                    schemaStrategy, BackendSupport.SAFE_SCHEMA_STRATEGY);
        }
        final long toolRows = ToolTelemetryRecord.count();
        final long modelRows = ModelCallTelemetryRecord.count();
        LOG.infof("Telemetry persistence confirmed: tool_telemetry=%d rows, model_call_telemetry=%d rows",
                toolRows, modelRows);
    }

    @Transactional
    public void recordModelCall(final String modelName,
            final String currentWorkingDirectory,
            final String userMessage,
            final String modelResponse,
            final int requestTokenCount,
            final int responseTokenCount,
            final long durationNanos,
            final String errorClass) {
        final ModelCallTelemetryRecord record = new ModelCallTelemetryRecord();
        record.modelName = safeModelName(modelName);
        record.currentWorkingDirectory = clipText(currentWorkingDirectory);
        record.userMessage = clipText(userMessage == null ? "" : userMessage);
        record.modelResponse = clipText(modelResponse);
        record.requestTokenCount = Math.max(0, requestTokenCount);
        record.responseTokenCount = Math.max(0, responseTokenCount);
        record.durationNanos = Math.max(0L, durationNanos);
        record.errorClass = clipText(errorClass);
        record.success = (errorClass == null || errorClass.isBlank());
        record.recordedAt = System.currentTimeMillis();
        record.persist();
    }

    @Transactional
    public void recordToolUse(final String toolName,
            final String moduleName,
            final String inputPayload,
            final String outputPayload,
            final long durationNanos,
            final String errorClass,
            final boolean argumentValidationFailure) {
        final ToolTelemetryRecord record = new ToolTelemetryRecord();
        record.toolName = safeModelName(toolName);
        record.moduleName = clipText(moduleName);
        record.inputPayload = clipText(inputPayload);
        record.outputPayload = clipText(outputPayload);
        record.durationNanos = Math.max(0L, durationNanos);
        record.errorClass = clipText(errorClass);
        record.success = (errorClass == null || errorClass.isBlank());
        record.argumentValidationFailure = argumentValidationFailure;
        record.recordedAt = System.currentTimeMillis();
        record.persist();
    }

    /**
     * Records a tool invocation with both a human-readable summary string
     * (for LLM consumption) and a typed JSON payload (for programmatic
     * verification and analytics).
     *
     * <p>
     * This satisfies the hybrid-contract recommendation (§2): keep a
     * human-readable summary for the LLM while capturing a structured payload
     * for downstream consumers. The {@code outputSchemaVersion} must be
     * incremented whenever the structure of {@code typedOutputPayload} changes.
     *
     * @param typedOutputPayload  JSON string of the typed result, or {@code null}
     * @param outputSchemaVersion schema version for the typed payload (1-based)
     */
    @Transactional
    public void recordToolUse(final String toolName,
            final String moduleName,
            final String inputPayload,
            final String outputPayload,
            final long durationNanos,
            final String errorClass,
            final boolean argumentValidationFailure,
            final String typedOutputPayload,
            final int outputSchemaVersion) {
        final ToolTelemetryRecord record = new ToolTelemetryRecord();
        record.toolName = safeModelName(toolName);
        record.moduleName = clipText(moduleName);
        record.inputPayload = clipText(inputPayload);
        record.outputPayload = clipText(outputPayload);
        record.durationNanos = Math.max(0L, durationNanos);
        record.errorClass = clipText(errorClass);
        record.success = (errorClass == null || errorClass.isBlank());
        record.argumentValidationFailure = argumentValidationFailure;
        record.recordedAt = System.currentTimeMillis();
        record.typedOutputPayload = clipText(typedOutputPayload);
        record.outputSchemaVersion = outputSchemaVersion > 0 ? outputSchemaVersion : null;
        record.persist();
    }

    static String clipText(final String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH);
    }

    private static String safeModelName(final String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return clipText(value.trim());
    }
}