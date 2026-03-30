package ac.uk.sussex.kn253.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.api.model.*;
import ac.uk.sussex.kn253.model.*;
import ac.uk.sussex.kn253.services.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class ProjectApiResourceTest {

    @Inject
    ProjectApiResource resource;

    @Inject
    WorkingDirectoryService workingDirectoryService;

    @Inject
    ToolActivityService toolActivityService;

    @Inject
    ToolTelemetryService toolTelemetryService;

    @BeforeEach
    @Transactional
    void clearDatabase() {
        ProjectKnowledge.deleteAll();
        Project.deleteAll();
        GitRepository.deleteAll();
        GithubRepository.deleteAll();
    }

    @Test
    void projectsAutoDiscoversCurrentDirectoryWhenDatabaseIsEmpty() {
        final var projects = resource.projects();

        assertFalse(projects.isEmpty(), "Expected at least one project after auto-discovery");

        final String cwd = Path.of("").toAbsolutePath().normalize().toString();
        assertTrue(projects.stream().map(ProjectSummaryResponse::directory).anyMatch(cwd::equals),
                "Expected current working directory to be included in discovered projects");
    }

    @Test
    void projectsScanFindsGitReposUnderCurrentWorkingDirectory() throws Exception {
        final Path tempRoot = Files.createTempDirectory("pdhd-scan-");
        Files.createDirectories(tempRoot.resolve("repo-a/.git"));
        Files.createDirectories(tempRoot.resolve("nested/repo-b/.git"));

        workingDirectoryService.navigateTo(tempRoot.toString());

        final var projects = resource.projects();

        final String repoA = tempRoot.resolve("repo-a").toAbsolutePath().normalize().toString();
        final String repoB = tempRoot.resolve("nested/repo-b").toAbsolutePath().normalize().toString();

        assertTrue(projects.stream().map(ProjectSummaryResponse::directory).anyMatch(repoA::equals),
                "Expected scan to discover repo-a");
        assertTrue(projects.stream().map(ProjectSummaryResponse::directory).anyMatch(repoB::equals),
                "Expected scan to discover nested/repo-b");
    }

    @Test
    @Transactional
    void knowledgeCanBeUpsertedAndListed() {
        final var projects = resource.projects();
        assertFalse(projects.isEmpty());
        final long id = projects.get(0).id();

        resource.putKnowledge(id, "summary", Map.of("jsonContent", "{\"text\":\"hello\"}"));

        final List<Map<String, Object>> knowledge = resource.listKnowledge(id);
        assertEquals(1, knowledge.size());
        assertEquals("summary", knowledge.get(0).get("key"));
        assertEquals("{\"text\":\"hello\"}", knowledge.get(0).get("jsonContent"));
    }

    @Test
    @Transactional
    void knowledgePutIsIdempotent() {
        final var projects = resource.projects();
        final long id = projects.get(0).id();

        resource.putKnowledge(id, "notes", Map.of("jsonContent", "\"v1\""));
        resource.putKnowledge(id, "notes", Map.of("jsonContent", "\"v2\""));

        final List<Map<String, Object>> knowledge = resource.listKnowledge(id);
        assertEquals(1, knowledge.size(), "Upsert should not create duplicates");
        assertEquals("\"v2\"", knowledge.get(0).get("jsonContent"));
    }

    @Test
    @Transactional
    void knowledgeCanBeDeleted() {
        final var projects = resource.projects();
        final long id = projects.get(0).id();

        resource.putKnowledge(id, "temp", Map.of("jsonContent", "{}"));
        assertEquals(1, resource.listKnowledge(id).size());

        resource.deleteKnowledge(id, "temp");
        assertEquals(0, resource.listKnowledge(id).size());
    }

    @Test
    void toolActivityV2IncludesSchemaMetadataAndEvents() {
        toolActivityService.record("read_file", "{\"filePath\":\"README.md\"}", "ok");

        final VersionedToolActivityResponse response = resource.toolActivityV2(20);

        assertEquals("tool-activity.v2", response.schemaVersion());
        assertNotNull(response.generatedAt());
        assertNotNull(response.summary());
        assertFalse(response.items().isEmpty());
    }

    @Test
    void toolTelemetryEndpointReturnsVersionedTypedPayload() {
        toolTelemetryService.record("read_file", "ReadToolset", 2_000_000L, null, false);
        toolTelemetryService.record("read_file", "ReadToolset", 3_000_000L, "ArgumentValidation", true);

        final ToolTelemetryResponse response = resource.toolTelemetry();

        assertEquals("tool-telemetry.v1", response.schemaVersion());
        assertNotNull(response.generatedAt());
        assertNotNull(response.summary());
        assertFalse(response.items().isEmpty());
        assertTrue(response.items().stream().anyMatch(item -> "read_file".equals(item.toolName())));
    }
}
