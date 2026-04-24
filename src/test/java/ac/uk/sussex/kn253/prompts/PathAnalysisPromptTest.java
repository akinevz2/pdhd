package ac.uk.sussex.kn253.prompts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.tools.WorkspaceContextTools;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Prompt spec: "Analyse the path {path} and return its metadata."
 *
 * Covers: docs/spec/path-analysis.md
 */
@QuarkusTest
class PathAnalysisPromptTest {

    @Inject
    WorkspaceContextTools workspaceContextTools;

    // ── happy path — file ─────────────────────────────────────────────────────

    @Test
    @Transactional
    void analyzePathDetailed_returnsMetadataForExistingFile() throws Exception {
        ProjectFolder.deleteAll();
        final Path dir = Files.createTempDirectory("pdhd-path-analysis-");
        final Path file = Files.writeString(dir.resolve("sample.txt"), "sample content");
        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(dir.toAbsolutePath().normalize().toString());
        project.setLoaded(true);
        project.persist();
        try {
            final String result = workspaceContextTools.analyzePathDetailed(file.toString());

            assertFalse(result.startsWith("Error"), result);
            assertTrue(result.contains(file.toAbsolutePath().normalize().toString()), result);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    @Transactional
    void analyzePathDetailed_includesTypeFieldForFile() throws Exception {
        ProjectFolder.deleteAll();
        final Path dir = Files.createTempDirectory("pdhd-path-type-");
        final Path file = Files.writeString(dir.resolve("typed.txt"), "content");
        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(dir.toAbsolutePath().normalize().toString());
        project.setLoaded(true);
        project.persist();
        try {
            final String result = workspaceContextTools.analyzePathDetailed(file.toString());

            assertTrue(
                    result.toLowerCase().contains("file") || result.toLowerCase().contains("type"),
                    "Expected type information in result: " + result);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }

    // ── happy path — directory ────────────────────────────────────────────────

    @Test
    @Transactional
    void analyzePathDetailed_returnsMetadataForDirectory() throws Exception {
        ProjectFolder.deleteAll();
        final Path dir = Files.createTempDirectory("pdhd-path-dir-");
        Files.createFile(dir.resolve("child.txt"));
        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(dir.toAbsolutePath().normalize().toString());
        project.setLoaded(true);
        project.persist();
        try {
            final String result = workspaceContextTools.analyzePathDetailed(dir.toString());

            assertFalse(result.startsWith("Error"), result);
            assertTrue(result.contains(dir.toAbsolutePath().normalize().toString()), result);
        } finally {
            Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (final Exception ignored) {
                }
            });
        }
    }

    // ── blank path defaults to cwd ────────────────────────────────────────────

    @Test
    void analyzePathDetailed_blankPathDefaultsToCurrentWorkingDirectory() {
        final String result = workspaceContextTools.analyzePathDetailed("");

        assertFalse(result.startsWith("Error"), result);
        assertTrue(result.startsWith("/") || result.contains("/"), result);
    }

    // ── failure / edge cases ──────────────────────────────────────────────────

    @Test
    void analyzePathDetailed_returnsErrorForNonExistentPath() {
        final String result = workspaceContextTools.analyzePathDetailed(
                "/tmp/pdhd-path-does-not-exist-xyz");

        assertTrue(result.startsWith("Error"), result);
    }
}
