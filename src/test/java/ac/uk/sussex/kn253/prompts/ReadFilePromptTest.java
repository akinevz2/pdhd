package ac.uk.sussex.kn253.prompts;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.tools.ReadFileTools;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Prompt spec: "Read the file at {path} and return its text content."
 *
 * Covers: docs/spec/read-file.md
 */
@QuarkusTest
class ReadFilePromptTest {

    @Inject
    ReadFileTools readFileTools;

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void readFile_returnsTextContentForFileInsideOpenProject() throws Exception {
        ProjectFolder.deleteAll();

        final Path projectDir = Files.createTempDirectory("pdhd-read-prompt-");
        final Path file = Files.writeString(projectDir.resolve("notes.txt"), "hello from prompt spec");
        try {
            final ProjectFolder project = new ProjectFolder();
            project.setDirectory(projectDir.toAbsolutePath().normalize().toString());
            project.setLoaded(true);
            project.persist();

            final String result = readFileTools.readFile(file.toString());

            assertEquals("hello from prompt spec", result);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(projectDir);
        }
    }

    @Test
    @Transactional
    void readFile_returnsFullContentForMultilineFile() throws Exception {
        ProjectFolder.deleteAll();

        final Path projectDir = Files.createTempDirectory("pdhd-read-multiline-");
        final String content = "line one\nline two\nline three";
        final Path file = Files.writeString(projectDir.resolve("multi.txt"), content);
        try {
            final ProjectFolder project = new ProjectFolder();
            project.setDirectory(projectDir.toAbsolutePath().normalize().toString());
            project.setLoaded(true);
            project.persist();

            final String result = readFileTools.readFile(file.toString());

            assertTrue(result.contains("line one"), result);
            assertTrue(result.contains("line three"), result);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(projectDir);
        }
    }

    // ── failure / edge cases ──────────────────────────────────────────────────

    @Test
    @Transactional
    void readFile_returnsAccessDeniedForFileOutsideAllProjects() throws Exception {
        ProjectFolder.deleteAll();

        final Path projectDir = Files.createTempDirectory("pdhd-read-open-");
        final Path outsideDir = Files.createTempDirectory("pdhd-read-outside-");
        final Path outsideFile = Files.writeString(outsideDir.resolve("secret.txt"), "forbidden");
        try {
            final ProjectFolder project = new ProjectFolder();
            project.setDirectory(projectDir.toAbsolutePath().normalize().toString());
            project.setLoaded(true);
            project.persist();

            final String result = readFileTools.readFile(outsideFile.toString());

            assertTrue(result.startsWith("Access denied:"), result);
        } finally {
            Files.deleteIfExists(outsideFile);
            Files.deleteIfExists(outsideDir);
            Files.deleteIfExists(projectDir);
        }
    }

    @Test
    @Transactional
    void readFile_returnsErrorForBlankPath() {
        ProjectFolder.deleteAll();

        final String result = readFileTools.readFile("   ");

        assertTrue(result.startsWith("Error:"), result);
        assertTrue(result.contains("blank"), result);
    }

    @Test
    @Transactional
    void readFile_returnsAccessDeniedWhenNoProjectsRegistered() throws Exception {
        ProjectFolder.deleteAll();

        final Path file = Files.writeString(
                Files.createTempFile("pdhd-read-noproj-", ".txt"), "some content");
        try {
            final String result = readFileTools.readFile(file.toString());

            assertTrue(result.startsWith("Access denied:"), result);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @Transactional
    void readFile_returnsErrorForNonExistentFile() throws Exception {
        ProjectFolder.deleteAll();

        final Path projectDir = Files.createTempDirectory("pdhd-read-missing-");
        try {
            final ProjectFolder project = new ProjectFolder();
            project.setDirectory(projectDir.toAbsolutePath().normalize().toString());
            project.setLoaded(true);
            project.persist();

            final String result = readFileTools.readFile(projectDir.resolve("does-not-exist.txt").toString());

            assertFalse(result.equals(""), result);
            // Either access denied (outside resolved roots) or a read error
            assertTrue(result.startsWith("Error") || result.startsWith("Access denied:"), result);
        } finally {
            Files.deleteIfExists(projectDir);
        }
    }
}
