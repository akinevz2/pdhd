package ac.uk.sussex.kn253.services.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.repository.*;
import ac.uk.sussex.kn253.services.TelemetryService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class FileSummarisationPipelineServiceTest {

    @Inject
    StructuredSummaryStoreService structuredSummaryStoreService;

    @Test
    @Transactional
    void summariseFolderAndStoreWithMetadataAcceptsFencedJson() {
        StructuredSummary.deleteAll();
        ProjectKnowledge.deleteAll();
        ProjectFolder.deleteAll();

        final ProjectFolder project = createProject("/tmp/pdhd-summary-pipeline");
        final FileSummarisationPipelineService service = buildService("```json\n"
                + "{\n"
                + "  \"purpose\": \"Coordinates startup checks.\",\n"
                + "  \"keyComponents\": [\"ModelConfigService\", \"TelemetryService\"],\n"
                + "  \"dependencies\": [\"Quarkus\", \"Panache\"]\n"
                + "}\n"
                + "```");

        final FileSummarisationPipelineService.FolderSummaryResult result = service
                .summariseFolderAndStoreWithMetadata(project, "src/main/java", "ignored folder context");

        assertNotNull(result.summary());
        assertEquals("Coordinates startup checks.", result.summary().getPurpose());
        assertEquals(SummaryType.FOLDER, result.summary().getSummaryType());
        assertEquals("src/main/java", result.summary().getTargetPath());
        assertEquals(1L, StructuredSummary.count());

        final ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project,
                "structured-summary:folder:src/main/java");
        assertNotNull(knowledge);
        assertTrue(knowledge.getJsonContent().contains("Coordinates startup checks."));
    }

    private FileSummarisationPipelineService buildService(final String response) {
        final FileSummarisationPipelineService service = new FileSummarisationPipelineService();
        service.fileSummarisationSubagent = new StubFileSummarisationSubagent(response);
        service.structuredSummaryStoreService = structuredSummaryStoreService;
        service.objectMapper = new ObjectMapper().findAndRegisterModules();
        service.telemetryService = new NoOpTelemetryService();
        return service;
    }

    private ProjectFolder createProject(final String directory) {
        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(directory);
        project.setLoaded(true);
        project.persist();
        return project;
    }

    private static final class StubFileSummarisationSubagent implements FileSummarisationSubagent {
        private final String response;

        private StubFileSummarisationSubagent(final String response) {
            this.response = response;
        }

        @Override
        public String summarise(final String fileContents, final String filePath) {
            return response;
        }

        @Override
        public String summariseFolder(final String folderSummaries, final String folderPath) {
            return response;
        }
    }

    private static final class NoOpTelemetryService extends TelemetryService {
        @Override
        public void recordToolUse(final String toolName, final String moduleName, final String inputPayload,
                final String outputPayload, final long durationNanos, final String errorClass,
                final boolean argumentValidationFailure) {
        }

        @Override
        public void recordToolUse(final String toolName, final String moduleName, final String inputPayload,
                final String outputPayload, final long durationNanos, final String errorClass,
                final boolean argumentValidationFailure, final String typedOutputPayload,
                final int outputSchemaVersion) {
        }
    }
}
