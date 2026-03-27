package ac.uk.sussex.kn253.services.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ac.uk.sussex.kn253.model.*;
import ac.uk.sussex.kn253.services.ToolService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class ProjectKnowledgeToolsetTest {

    @Inject
    ToolService toolService;

    @BeforeEach
    @Transactional
    void clearDatabase() {
        ProjectKnowledge.deleteAll();
        Project.deleteAll();
        GitRepository.deleteAll();
        GithubRepository.deleteAll();
    }

    @Test
    void cacheProjectKnowledgeAccumulatesTaggedEntriesAcrossQueries(@TempDir final Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("src"));

        final String first = toolService.execute(request(
                "cache_project_knowledge",
                "{\"projectDirectory\":\"" + escape(tempDir)
                        + "\",\"tag\":\"requirements\",\"query\":\"add markdown rendering\",\"note\":\"User wants explorer canvas to render markdown\"}"),
                null);
        final String second = toolService.execute(request(
                "cache_project_knowledge",
                "{\"projectDirectory\":\"" + escape(tempDir)
                        + "\",\"tag\":\"requirements\",\"query\":\"fix file pane reads\",\"note\":\"Regular file opens must use absolute-path fs API\"}"),
                null);
        final String recalled = toolService.execute(request(
                "read_project_knowledge",
                "{\"projectDirectory\":\"" + escape(tempDir) + "\",\"tag\":\"requirements\"}"), null);

        assertTrue(first.contains("entries=1"));
        assertTrue(second.contains("entries=2"));
        assertTrue(recalled.contains("entries=2"));
        assertTrue(recalled.contains("add markdown rendering"));
        assertTrue(recalled.contains("fix file pane reads"));
        assertTrue(recalled.contains("render markdown"));
        assertTrue(recalled.contains("absolute-path fs API"));
    }

    @Test
    void readProjectKnowledgeListsAvailableTags(@TempDir final Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("src"));

        toolService.execute(request(
                "cache_project_knowledge",
                "{\"projectDirectory\":\"" + escape(tempDir)
                        + "\",\"tag\":\"requirements\",\"note\":\"Need project canvas markdown support\"}"),
                null);
        toolService.execute(request(
                "cache_project_knowledge",
                "{\"projectDirectory\":\"" + escape(tempDir)
                        + "\",\"tag\":\"decisions\",\"note\":\"Use absolute fs endpoints for file previews\"}"),
                null);

        final String recalled = toolService.execute(request(
                "read_project_knowledge",
                "{\"projectDirectory\":\"" + escape(tempDir) + "\"}"), null);

        assertTrue(recalled.contains("tags=2"));
        assertTrue(recalled.contains("tag=decisions entries=1"));
        assertTrue(recalled.contains("tag=requirements entries=1"));
        assertTrue(recalled.contains("Use tag=<name> to read the full cached JSON"));
    }

    @Test
    void projectKnowledgeIsScopedPerProject(@TempDir final Path tempDir) throws Exception {
        final Path projectA = tempDir.resolve("project-a");
        final Path projectB = tempDir.resolve("project-b");
        Files.createDirectories(projectA);
        Files.createDirectories(projectB);

        toolService.execute(request(
                "cache_project_knowledge",
                "{\"projectDirectory\":\"" + escape(projectA)
                        + "\",\"tag\":\"bugs\",\"note\":\"A: stale cwd payload must be ignored\"}"),
                null);
        toolService.execute(request(
                "cache_project_knowledge",
                "{\"projectDirectory\":\"" + escape(projectB)
                        + "\",\"tag\":\"bugs\",\"note\":\"B: regular file pane reads must resolve absolute paths\"}"),
                null);

        final String recalledA = toolService.execute(request(
                "read_project_knowledge",
                "{\"projectDirectory\":\"" + escape(projectA) + "\",\"tag\":\"bugs\"}"), null);
        final String recalledB = toolService.execute(request(
                "read_project_knowledge",
                "{\"projectDirectory\":\"" + escape(projectB) + "\",\"tag\":\"bugs\"}"), null);

        assertTrue(recalledA.contains("stale cwd payload"));
        assertFalse(recalledA.contains("regular file pane reads"));
        assertTrue(recalledB.contains("regular file pane reads"));
        assertFalse(recalledB.contains("stale cwd payload"));
    }

    private ToolExecutionRequest request(final String name, final String jsonArguments) {
        return ToolExecutionRequest.builder().name(name).arguments(jsonArguments).build();
    }

    private String escape(final Path path) {
        return path.toString().replace("\\", "\\\\");
    }
}