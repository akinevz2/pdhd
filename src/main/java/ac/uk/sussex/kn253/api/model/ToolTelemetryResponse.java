package ac.uk.sussex.kn253.api.model;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;

import ac.uk.sussex.kn253.services.ToolTelemetryService.ToolTelemetrySnapshot;

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

                public ToolTelemetryItem(final ToolTelemetrySnapshot snapshot) {
                        this(
                                        snapshot.toolName(),
                                        snapshot.moduleName(),
                                        snapshot.invocations(),
                                        snapshot.failures(),
                                        snapshot.argumentValidationFailures(),
                                        snapshot.averageDurationMs(),
                                        snapshot.p50DurationMs(),
                                        snapshot.p95DurationMs(),
                                        snapshot.errorClasses());
                }
        }
}
