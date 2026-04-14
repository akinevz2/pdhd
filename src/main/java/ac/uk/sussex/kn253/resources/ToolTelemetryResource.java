package ac.uk.sussex.kn253.resources;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import ac.uk.sussex.kn253.repository.ToolTelemetryRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/tool-telemetry")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ToolTelemetryResource {

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

    public record ToolTelemetryResponse(
            String schemaVersion,
            String generatedAt,
            String summary,
            List<ToolTelemetryItem> items) {
    }

    @GET
    @Transactional
    public ToolTelemetryResponse getTelemetry() {
        final List<ToolTelemetryRecord> records = ToolTelemetryRecord.listAll();

        final Map<String, List<ToolTelemetryRecord>> byTool = records.stream()
                .collect(Collectors.groupingBy(r -> r.toolName + "|" + (r.moduleName == null ? "" : r.moduleName)));

        final List<ToolTelemetryItem> items = byTool.values().stream()
                .map(ToolTelemetryResource::toItem)
                .sorted(Comparator.comparing(ToolTelemetryItem::toolName))
                .collect(Collectors.toList());

        final long totalInvocations = items.stream().mapToLong(ToolTelemetryItem::invocations).sum();
        final long totalFailures = items.stream().mapToLong(ToolTelemetryItem::failures).sum();
        final String summary = items.isEmpty()
                ? "No tool invocations recorded."
                : String.format("%d invocation(s) across %d tool(s); %d failure(s).",
                        totalInvocations, items.size(), totalFailures);

        final String generatedAt = DateTimeFormatter.ISO_INSTANT
                .format(Instant.now().atOffset(ZoneOffset.UTC));

        return new ToolTelemetryResponse("1", generatedAt, summary, items);
    }

    private static ToolTelemetryItem toItem(final List<ToolTelemetryRecord> group) {
        final ToolTelemetryRecord first = group.get(0);
        final long invocations = group.size();
        final long failures = group.stream().filter(r -> !r.success).count();
        final long argFailures = group.stream().filter(r -> r.argumentValidationFailure).count();

        final long[] durationsNanos = group.stream()
                .mapToLong(r -> r.durationNanos)
                .sorted()
                .toArray();

        final double avgMs = Arrays.stream(durationsNanos).average().orElse(0) / 1_000_000.0;
        final double p50Ms = percentile(durationsNanos, 50) / 1_000_000.0;
        final double p95Ms = percentile(durationsNanos, 95) / 1_000_000.0;

        final Map<String, Long> errorClasses = group.stream()
                .filter(r -> r.errorClass != null && !r.errorClass.isBlank())
                .collect(Collectors.groupingBy(r -> r.errorClass, Collectors.counting()));

        return new ToolTelemetryItem(
                first.toolName,
                first.moduleName == null ? "" : first.moduleName,
                invocations,
                failures,
                argFailures,
                round2(avgMs),
                round2(p50Ms),
                round2(p95Ms),
                errorClasses);
    }

    private static double percentile(final long[] sorted, final int pct) {
        if (sorted.length == 0) {
            return 0;
        }
        final int index = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static double round2(final double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
