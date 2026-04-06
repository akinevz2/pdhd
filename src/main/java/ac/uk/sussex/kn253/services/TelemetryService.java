package ac.uk.sussex.kn253.services;

import ac.uk.sussex.kn253.repository.ModelCallTelemetryRecord;
import ac.uk.sussex.kn253.repository.ToolTelemetryRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Records model-call and tool-use telemetry in persistent tables.
 */
@ApplicationScoped
public class TelemetryService {

    static final int MAX_TEXT_LENGTH = 8_000;

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