package ac.uk.sussex.kn253.resources.api.model;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;

/** Versioned response body for tool telemetry metrics. */
public record ToolTelemetryResponse(
                String schemaVersion,
                String generatedAt,
                String summary,
                List<@NonNull ToolTelemetryItem> items) {

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
