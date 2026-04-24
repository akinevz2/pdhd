package ac.uk.sussex.kn253.resources;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.ToolTelemetryRecord;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class ToolTelemetryResourceTest {

    @Inject
    ToolTelemetryResource toolTelemetryResource;

    @BeforeEach
    @Transactional
    void cleanUp() {
        ToolTelemetryRecord.deleteAll();
    }

    @Test
    @Transactional
    void telemetryReturnsSchemaVersionOneAndEmptySummaryWhenNoRecords() {
        final ToolTelemetryResource.ToolTelemetryResponse response = toolTelemetryResource.telemetry();

        assertEquals("1", response.schemaVersion());
        assertNotNull(response.generatedAt(), "generatedAt must not be null");
        assertEquals("No tool invocations recorded.", response.summary());
        assertNotNull(response.items());
        assertTrue(response.items().isEmpty());
    }

    @Test
    @Transactional
    void telemetryAggregatesInvocationCountByToolAndModule() {
        persistRecord("get_workspace_context", "WORKSPACE", false, 100_000_000L);
        persistRecord("get_workspace_context", "WORKSPACE", false, 200_000_000L);
        persistRecord("read_file_content", "READ_FILE", true, 50_000_000L);

        final ToolTelemetryResource.ToolTelemetryResponse response = toolTelemetryResource.telemetry();

        assertEquals(2, response.items().size(), "Expected 2 distinct tool entries");

        final ToolTelemetryResource.ToolTelemetryItem wsItem = response.items().stream()
                .filter(i -> "get_workspace_context".equals(i.toolName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("get_workspace_context not found in telemetry"));
        assertEquals(2L, wsItem.invocations());
        assertEquals(0L, wsItem.failures());
        assertEquals("WORKSPACE", wsItem.moduleName());

        final ToolTelemetryResource.ToolTelemetryItem rfItem = response.items().stream()
                .filter(i -> "read_file_content".equals(i.toolName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("read_file_content not found in telemetry"));
        assertEquals(1L, rfItem.invocations());
        assertEquals(1L, rfItem.failures());
        assertEquals("READ_FILE", rfItem.moduleName());
    }

    @Test
    @Transactional
    void telemetrySummaryReflectsTotalInvocationsAndFailures() {
        persistRecord("tool_a", "WORKSPACE", false, 10_000_000L);
        persistRecord("tool_a", "WORKSPACE", true, 20_000_000L);
        persistRecord("tool_b", "GIT", false, 30_000_000L);

        final ToolTelemetryResource.ToolTelemetryResponse response = toolTelemetryResource.telemetry();

        assertTrue(response.summary().contains("3 invocation(s)"),
                "summary should report 3 total invocations, got: " + response.summary());
        assertTrue(response.summary().contains("2 tool(s)"),
                "summary should report 2 distinct tools, got: " + response.summary());
        assertTrue(response.summary().contains("1 failure(s)"),
                "summary should report 1 failure, got: " + response.summary());
    }

    @Test
    @Transactional
    void telemetryComputesAverageDurationInMilliseconds() {
        persistRecord("tool_timing", "WORKSPACE", false, 100_000_000L); // 100 ms
        persistRecord("tool_timing", "WORKSPACE", false, 300_000_000L); // 300 ms

        final ToolTelemetryResource.ToolTelemetryResponse response = toolTelemetryResource.telemetry();
        final ToolTelemetryResource.ToolTelemetryItem item = response.items().stream()
                .filter(i -> "tool_timing".equals(i.toolName()))
                .findFirst()
                .orElseThrow();

        assertEquals(200.0, item.averageDurationMs(), 0.01,
                "Average duration should be 200 ms for 100 ms + 300 ms");
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private void persistRecord(final String toolName, final String moduleName,
            final boolean failed, final long durationNanos) {
        final ToolTelemetryRecord record = new ToolTelemetryRecord();
        record.toolName = toolName;
        record.moduleName = moduleName;
        record.success = !failed;
        record.durationNanos = durationNanos;
        record.recordedAt = System.currentTimeMillis();
        record.argumentValidationFailure = false;
        record.persist();
    }
}
