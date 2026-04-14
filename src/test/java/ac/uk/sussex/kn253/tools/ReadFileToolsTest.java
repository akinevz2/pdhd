package ac.uk.sussex.kn253.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class ReadFileToolsTest {

    @Inject
    ReadFileTools readFileTools;

    @Test
    @Transactional
    void readFileSucceedsForFileInsideOpenProject() throws Exception {
        ProjectFolder.deleteAll();

        final Path projectDir = Files.createTempDirectory("pdhd-readfile-open-");
        final Path testFile = Files.writeString(projectDir.resolve("example.txt"), "hello world");
        try {
            final ProjectFolder project = new ProjectFolder();
            project.setDirectory(projectDir.toAbsolutePath().normalize().toString());
            project.setLoaded(true);
            project.persist();

            final String result = readFileTools.readFile(testFile.toString());

            assertEquals("hello world", result);
        } finally {
            Files.deleteIfExists(testFile);
            Files.deleteIfExists(projectDir);
        }
    }

    @Test
    @Transactional
    void readFileReturnsAccessDeniedForFileOutsideOpenProjects() throws Exception {
        ProjectFolder.deleteAll();

        final Path openProjectDir = Files.createTempDirectory("pdhd-readfile-project-");
        final Path outsideDir = Files.createTempDirectory("pdhd-readfile-outside-");
        final Path testFile = Files.writeString(outsideDir.resolve("secret.txt"), "secret");
        try {
            final ProjectFolder project = new ProjectFolder();
            project.setDirectory(openProjectDir.toAbsolutePath().normalize().toString());
            project.setLoaded(true);
            project.persist();

            final String result = readFileTools.readFile(testFile.toString());
            assertTrue(result.startsWith("Access denied:"));
        } finally {
            Files.deleteIfExists(testFile);
            Files.deleteIfExists(outsideDir);
            Files.deleteIfExists(openProjectDir);
        }
    }

    @Test
    @Transactional
    void readFileReturnsAccessDeniedWhenNoProjectsAreOpen() throws Exception {
        ProjectFolder.deleteAll();

        final Path tempFile = Files.writeString(
                Files.createTempFile("pdhd-readfile-noproject-", ".txt"), "data");
        try {
            final String result = readFileTools.readFile(tempFile.toString());
            assertTrue(result.startsWith("Access denied:"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
