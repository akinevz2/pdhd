package ac.uk.sussex.kn253.services;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

import ac.uk.sussex.kn253.repository.ToolTelemetryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * In-memory telemetry aggregator for tool dispatch and execution.
 *
 * <p>
 * Stores per-tool execution counts, latency summaries, and categorized failures
 * so operators can diagnose regressions as the toolset grows.
 *
 * <p>
 * Each call is also persisted to the {@code tool_telemetry} table via
 * {@link ToolTelemetryRepository} so that records survive application restarts
 * and are never lost due to schema changes (the {@code update} strategy never
 * drops the table).
 */
@ApplicationScoped
public class ToolTelemetryService {

    private static final int MAX_LATENCY_SAMPLES = 200;

    private final ConcurrentMap<String, ToolStats> statsByTool = new ConcurrentHashMap<>();

    @Inject
    ToolTelemetryRepository repository;

    public void record(
            final String toolName,
            final String moduleName,
            final long durationNanos,
            final String errorClass,
            final boolean argumentValidationFailure) {
        final String safeToolName = (toolName == null || toolName.isBlank()) ? "<unknown>" : toolName;
        statsByTool.computeIfAbsent(safeToolName, ignored -> new ToolStats())
                .record(moduleName, durationNanos, errorClass, argumentValidationFailure);
        if (repository != null) {
            try {
                repository.save(safeToolName, moduleName, durationNanos, errorClass, argumentValidationFailure);
            } catch (final Exception ignored) {
                // telemetry persistence must never interfere with tool execution
            }
        }
    }

    public List<ToolTelemetrySnapshot> snapshot() {
        final List<ToolTelemetrySnapshot> snapshots = new ArrayList<>();
        for (final Map.Entry<String, ToolStats> entry : statsByTool.entrySet()) {
            snapshots.add(entry.getValue().toSnapshot(entry.getKey()));
        }
        snapshots.sort((a, b) -> a.toolName().compareToIgnoreCase(b.toolName()));
        return snapshots;
    }

    private static final class ToolStats {

        private final LongAdder invocations = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder argumentValidationFailures = new LongAdder();
        private final LongAdder totalDurationNanos = new LongAdder();
        private final ConcurrentMap<String, LongAdder> errorsByClass = new ConcurrentHashMap<>();

        private final ArrayDeque<Long> latencySamplesNanos = new ArrayDeque<>();
        private volatile String moduleName;

        void record(
                final String moduleName,
                final long durationNanos,
                final String errorClass,
                final boolean argumentValidationFailure) {
            invocations.increment();
            totalDurationNanos.add(Math.max(0L, durationNanos));
            this.moduleName = (moduleName == null || moduleName.isBlank()) ? "unknown" : moduleName;

            if (argumentValidationFailure) {
                argumentValidationFailures.increment();
            }

            if (errorClass != null && !errorClass.isBlank()) {
                failures.increment();
                errorsByClass.computeIfAbsent(errorClass, ignored -> new LongAdder()).increment();
            }

            synchronized (latencySamplesNanos) {
                latencySamplesNanos.addLast(Math.max(0L, durationNanos));
                while (latencySamplesNanos.size() > MAX_LATENCY_SAMPLES) {
                    latencySamplesNanos.removeFirst();
                }
            }
        }

        ToolTelemetrySnapshot toSnapshot(final String toolName) {
            final long callCount = invocations.sum();
            final double averageMs = callCount == 0
                    ? 0.0
                    : nanosToMillis(totalDurationNanos.sum()) / callCount;

            final List<Long> latencies;
            synchronized (latencySamplesNanos) {
                latencies = new ArrayList<>(latencySamplesNanos);
            }
            Collections.sort(latencies);

            final Map<String, Long> errorCounts = new LinkedHashMap<>();
            errorsByClass.forEach((name, counter) -> errorCounts.put(name, counter.sum()));

            return new ToolTelemetrySnapshot(
                    toolName,
                    moduleName == null ? "unknown" : moduleName,
                    callCount,
                    failures.sum(),
                    argumentValidationFailures.sum(),
                    averageMs,
                    percentileMs(latencies, 50),
                    percentileMs(latencies, 95),
                    Map.copyOf(errorCounts));
        }

        private double percentileMs(final List<Long> sortedLatenciesNanos, final int percentile) {
            if (sortedLatenciesNanos.isEmpty()) {
                return 0.0;
            }
            final int index = (int) Math.ceil((percentile / 100.0) * sortedLatenciesNanos.size()) - 1;
            final int boundedIndex = Math.max(0, Math.min(index, sortedLatenciesNanos.size() - 1));
            return nanosToMillis(sortedLatenciesNanos.get(boundedIndex));
        }

        private double nanosToMillis(final long nanos) {
            return nanos / 1_000_000.0;
        }
    }

    public record ToolTelemetrySnapshot(
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
