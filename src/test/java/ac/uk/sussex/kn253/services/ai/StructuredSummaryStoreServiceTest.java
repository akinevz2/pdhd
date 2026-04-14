package ac.uk.sussex.kn253.services.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class StructuredSummaryStoreServiceTest {

    @Inject
    StructuredSummaryStoreService storeService;

    @Test
    @Transactional
    void upsertCreatesStructuredSummaryAndMirrorsProjectKnowledge() {
        StructuredSummary.deleteAll();
        ProjectKnowledge.deleteAll();
        ProjectFolder.deleteAll();

        final ProjectFolder project = createProject("/tmp/pdhd-structured-a");
        final StructuredSummaryStoreService.StructuredSummaryPayload payload = new StructuredSummaryStoreService.StructuredSummaryPayload(
                "Provides API endpoints for projects.",
                List.of("ProjectApiResource", "ProjectService"),
                List.of("jakarta.ws.rs", "Panache"));

        final StructuredSummary summary = storeService.upsert(project, SummaryType.FILE,
                "src/main/java/ProjectApiResource.java", payload);

        assertNotNull(summary.id);
        assertEquals(SummaryType.FILE, summary.getSummaryType());
        assertEquals("src/main/java/ProjectApiResource.java", summary.getTargetPath());
        assertEquals("Provides API endpoints for projects.", summary.getPurpose());

        final String knowledgeKey = "structured-summary:file:src/main/java/ProjectApiResource.java";
        final ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project, knowledgeKey);
        assertNotNull(knowledge);
        assertTrue(knowledge.getJsonContent().contains("\"purpose\":\"Provides API endpoints for projects.\""));
    }

    @Test
    @Transactional
    void upsertIsIdempotentWhenPayloadIsUnchanged() {
        StructuredSummary.deleteAll();
        ProjectKnowledge.deleteAll();
        ProjectFolder.deleteAll();

        final ProjectFolder project = createProject("/tmp/pdhd-structured-b");
        final StructuredSummaryStoreService.StructuredSummaryPayload payload = new StructuredSummaryStoreService.StructuredSummaryPayload(
                "Coordinates startup checks.",
                List.of("OllamaStartupCoordinator"),
                List.of("OllamaManagementService"));

        final StructuredSummary first = storeService.upsert(project, SummaryType.FILE,
                "src/main/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinator.java", payload);
        final Instant firstUpdatedAt = first.getUpdatedAt();
        final Long firstId = first.id;

        final StructuredSummary second = storeService.upsert(project, SummaryType.FILE,
                "src/main/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinator.java", payload);

        assertEquals(firstId, second.id);
        assertEquals(firstUpdatedAt, second.getUpdatedAt());
        assertEquals(1L, StructuredSummary.count());
    }

    @Test
    @Transactional
    void upsertUpdatesWhenPayloadChanges() {
        StructuredSummary.deleteAll();
        ProjectKnowledge.deleteAll();
        ProjectFolder.deleteAll();

        final ProjectFolder project = createProject("/tmp/pdhd-structured-c");
        final String path = "src/main/java/ac/uk/sussex/kn253/services/ai/FileSummarisationSubagent.java";

        final StructuredSummaryStoreService.StructuredSummaryPayload firstPayload = new StructuredSummaryStoreService.StructuredSummaryPayload(
                "Summarises files.",
                List.of("summarise"),
                List.of("langchain4j"));
        final StructuredSummary first = storeService.upsert(project, SummaryType.FILE, path, firstPayload);
        final Instant firstUpdatedAt = first.getUpdatedAt();
        final String firstHash = first.getContentHash();

        final StructuredSummaryStoreService.StructuredSummaryPayload secondPayload = new StructuredSummaryStoreService.StructuredSummaryPayload(
                "Summarises files and folders.",
                List.of("summarise", "summariseFolder"),
                List.of("langchain4j", "quarkus"));
        final StructuredSummary second = storeService.upsert(project, SummaryType.FILE, path, secondPayload);

        assertNotEquals(firstHash, second.getContentHash());
        assertTrue(!second.getUpdatedAt().isBefore(firstUpdatedAt));

        final ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project,
                "structured-summary:file:" + path);
        assertNotNull(knowledge);
        assertTrue(knowledge.getJsonContent().contains("\"purpose\":\"Summarises files and folders.\""));
    }

        @Test
        @Transactional
        void upsertUsesBoundedKnowledgeKeyForLongPath() {
                StructuredSummary.deleteAll();
                ProjectKnowledge.deleteAll();
                ProjectFolder.deleteAll();

                final ProjectFolder project = createProject("/tmp/pdhd-structured-long");
                final String longPath = "src/" + "a".repeat(490) + ".java";
                final StructuredSummaryStoreService.StructuredSummaryPayload payload = new StructuredSummaryStoreService.StructuredSummaryPayload(
                                "Summarises a long path file.",
                                List.of("LongPathType"),
                                List.of("jakarta"));

                final StructuredSummary summary = storeService.upsert(project, SummaryType.FILE, longPath, payload);

                assertNotNull(summary.getKnowledgeRefKey());
                assertTrue(summary.getKnowledgeRefKey().length() <= 200);
                assertTrue(summary.getKnowledgeRefKey().contains(":sha256:"));

                final ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project, summary.getKnowledgeRefKey());
                assertNotNull(knowledge);
                assertTrue(knowledge.getJsonContent().contains("\"targetPath\":\"" + longPath + "\""));
        }

        @Test
        @Transactional
        void upsertRejectsTargetPathLongerThanDatabaseColumn() {
                StructuredSummary.deleteAll();
                ProjectKnowledge.deleteAll();
                ProjectFolder.deleteAll();

                final ProjectFolder project = createProject("/tmp/pdhd-structured-too-long");
                final String tooLongPath = "x".repeat(513);
                final StructuredSummaryStoreService.StructuredSummaryPayload payload = new StructuredSummaryStoreService.StructuredSummaryPayload(
                                "Will fail due to path length.",
                                List.of("One"),
                                List.of("Two"));

                final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                () -> storeService.upsert(project, SummaryType.FILE, tooLongPath, payload));

                assertTrue(error.getMessage().contains("<= 512"));
        }

    private ProjectFolder createProject(final String directory) {
        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(directory);
        project.setLoaded(true);
        project.persist();
        return project;
    }
}
