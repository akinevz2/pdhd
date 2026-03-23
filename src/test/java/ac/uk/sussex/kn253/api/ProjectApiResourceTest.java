package ac.uk.sussex.kn253.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.api.model.ProjectSummaryResponse;
import ac.uk.sussex.kn253.model.*;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class ProjectApiResourceTest {

    @Inject
    ProjectApiResource resource;

    @Inject
    WorkingDirectoryService workingDirectoryService;

    @BeforeEach
    @Transactional
    void clearDatabase() {
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
}
