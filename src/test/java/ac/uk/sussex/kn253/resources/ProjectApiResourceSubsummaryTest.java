package ac.uk.sussex.kn253.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class SummaryResourceSubsummaryTest {

    @Inject
    SummaryResource summaryApiResource;

    @Test
    @Transactional
    void listFolderSubsummariesReturnsOnlyRequestedFolderEntries() throws Exception {
        StructuredSummary.deleteAll();
        ProjectKnowledge.deleteAll();
        ProjectFolder.deleteAll();

        final Path root = Files.createTempDirectory("pdhd-folder-subsummary-test-");
        try {
            final Path targetFolder = root.resolve("src/main/java");
            Files.createDirectories(targetFolder.resolve("util"));
            Files.writeString(targetFolder.resolve("App.java"), "class App {}", StandardCharsets.UTF_8);
            Files.writeString(targetFolder.resolve("util/Helper.java"), "class Helper {}", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("README.md"), "# docs", StandardCharsets.UTF_8);

            final ProjectFolder project = createProject(root);

            final Instant now = Instant.now();
            createSummary(project, SummaryType.FILE, "src/main/java/App.java", "Main application entrypoint",
                    now.minusSeconds(10));
            createSummary(project, SummaryType.FILE, "src/main/java/util/Helper.java", "Helper utilities", now);
            createSummary(project, SummaryType.FILE, "README.md", "Project docs", now.plusSeconds(10));

            final String folderUuid = uuidForPath(targetFolder);
            final SummaryResource.FolderSubsummaryResponse response = summaryApiResource
                    .file(new SummaryResource.SummaryRequest(project.id, folderUuid));

            assertEquals("src/main/java", response.folderPath());
            assertEquals(2, response.count());
            assertEquals(2, response.items().size());
            assertTrue(response.items().stream().anyMatch(i -> "src/main/java/App.java".equals(i.targetPath())));
            assertTrue(
                    response.items().stream().anyMatch(i -> "src/main/java/util/Helper.java".equals(i.targetPath())));
            assertTrue(response.items().stream().noneMatch(i -> "README.md".equals(i.targetPath())));
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    @Transactional
    void folderSummaryStatusReflectsPersistedFolderSummaryOnly() throws Exception {
        StructuredSummary.deleteAll();
        ProjectKnowledge.deleteAll();
        ProjectFolder.deleteAll();

        final Path root = Files.createTempDirectory("pdhd-folder-status-test-");
        try {
            final Path targetFolder = root.resolve("src/main/java");
            Files.createDirectories(targetFolder);
            Files.writeString(targetFolder.resolve("App.java"), "class App {}", StandardCharsets.UTF_8);

            final ProjectFolder project = createProject(root);
            final Instant now = Instant.now();

            // File summaries alone must not mark folder summary status as existing.
            createSummary(project, SummaryType.FILE, "src/main/java/App.java", "Main application entrypoint", now);

            final String folderUuid = uuidForPath(targetFolder);
            final SummaryResource.FolderSummaryStatusResponse before = summaryApiResource
                    .status(new SummaryResource.SummaryRequest(project.id, folderUuid));

            assertEquals("src/main/java", before.folderPath());
            assertFalse(before.exists());

            createSummary(project, SummaryType.FOLDER, "src/main/java", "Folder overview", now.plusSeconds(5));

            final SummaryResource.FolderSummaryStatusResponse after = summaryApiResource
                    .status(new SummaryResource.SummaryRequest(project.id, folderUuid));

            assertEquals("src/main/java", after.folderPath());
            assertTrue(after.exists());
            assertTrue(after.updatedAt() != null && !after.updatedAt().isBlank());
        } finally {
            deleteRecursively(root);
        }
    }

    private ProjectFolder createProject(final Path root) {
        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(root.toAbsolutePath().normalize().toString());
        project.setLoaded(true);
        project.persist();
        return project;
    }

    private void createSummary(
            final ProjectFolder project,
            final SummaryType summaryType,
            final String targetPath,
            final String purpose,
            final Instant updatedAt) {
        final StructuredSummary summary = new StructuredSummary();
        summary.setProject(project);
        summary.setSummaryType(summaryType);
        summary.setTargetPath(targetPath);
        summary.setPurpose(purpose);
        summary.setKeyComponentsJson("[]");
        summary.setDependenciesJson("[]");
        summary.setContentHash(UUID.randomUUID().toString().replace("-", ""));
        summary.setCreatedAt(updatedAt);
        summary.setUpdatedAt(updatedAt);
        summary.persist();
    }

    private String uuidForPath(final Path path) {
        final String normalized = path.toAbsolutePath().normalize().toString();
        return UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void deleteRecursively(final Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (final Exception ignored) {
                }
            });
        } catch (final Exception ignored) {
        }
    }
}
