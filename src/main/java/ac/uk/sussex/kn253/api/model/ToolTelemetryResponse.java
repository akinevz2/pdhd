package ac.uk.sussex.kn253.api.model;

import java.util.List;
import java.util.Map;

/** Versioned response body for tool telemetry metrics. */
public record ToolTelemetryResponse(
        String schemaVersion,
        String generatedAt,
        String summary,
        List<ToolTelemetryItem> items) {

    public record ToolTelemetryItem(
            String toolName,
            String moduleName,
            long invocations,
            long failures,
            long argumentValidationFailures,
            double averageDurationMs,
            double p50DurationMs,
            double p95DurationMs,
            Map<String, Long> errorClasses) {
    }
}
