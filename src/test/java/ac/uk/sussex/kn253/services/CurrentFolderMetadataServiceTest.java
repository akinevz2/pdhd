package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ac.uk.sussex.kn253.model.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class CurrentFolderMetadataServiceTest {

    @Inject
    CurrentFolderMetadataService currentFolderMetadataService;

    @Inject
    WorkingDirectoryService workingDirectoryService;

    @BeforeEach
    @Transactional
    void clearDatabase() {
        ProjectKnowledge.deleteAll();
        Project.deleteAll();
        GitRepository.deleteAll();
        GithubRepository.deleteAll();
    }

    @Test
    @Transactional
    void promptContextMarksFolderAsPreviouslyWorkedOnWhenKnowledgeExists(@TempDir final Path tempDir) throws Exception {
        Files.createDirectories(tempDir);
        workingDirectoryService.navigateTo(tempDir.toString());

        final Project project = new Project(null, tempDir.toAbsolutePath().normalize().toString(), null, null);
        project.persist();
        final String json = """
                {
                  "tag": "requirements",
                  "projectDirectory": "%s",
                  "entries": [
                    {"timestamp": "%s", "note": "Keep markdown rendering in the canvas"}
                  ]
                }
                """.formatted(tempDir.toAbsolutePath().normalize(), Instant.now());
        new ProjectKnowledge(null, project, "requirements", json, Instant.now(), Instant.now()).persist();

        final String context = currentFolderMetadataService.buildPromptContext();

        assertTrue(context.contains("cwd: " + tempDir.toAbsolutePath().normalize()));
        assertTrue(context.contains("cachedKnowledgeTagCount: 1"));
        assertTrue(context.contains("cachedKnowledgeTags: requirements(1)"));
        assertTrue(context.contains("previouslyWorkedOnHere: true"));
        assertTrue(context.contains("prefer read_project_knowledge"));
    }

    @Test
    void promptContextMarksFolderAsNewWhenNoKnowledgeExists(@TempDir final Path tempDir) throws Exception {
        Files.createDirectories(tempDir);
        workingDirectoryService.navigateTo(tempDir.toString());

        final String context = currentFolderMetadataService.buildPromptContext();

        assertTrue(context.contains("cachedKnowledgeTagCount: 0"));
        assertTrue(context.contains("previouslyWorkedOnHere: false"));
        assertTrue(context.contains(
                "meaning: previouslyWorkedOnHere is true only when this tagged folder has one or more cached project knowledge records."));
    }
}