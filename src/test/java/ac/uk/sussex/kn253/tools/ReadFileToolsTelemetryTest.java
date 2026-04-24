package ac.uk.sussex.kn253.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.repository.ToolTelemetryRecord;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class ReadFileToolsTelemetryTest {

    @Inject
    ReadFileTools readFileTools;

    @Test
    @Transactional
    void readMeIntentRecordsReadFileToolTelemetry() {
        ToolTelemetryRecord.deleteAll();
        ProjectFolder.deleteAll();

        final String result = readFileTools.readFile("README.md");

        assertTrue(!result.startsWith("Access denied:"));

        final long rows = ToolTelemetryRecord.count();
        assertEquals(1L, rows);

        final ToolTelemetryRecord record = ToolTelemetryRecord.findAll().firstResult();
        assertEquals("readFile", record.toolName);
        assertEquals("READ_FILE", record.moduleName);
        assertTrue(record.success);
        assertEquals(false, record.argumentValidationFailure);
    }
}
