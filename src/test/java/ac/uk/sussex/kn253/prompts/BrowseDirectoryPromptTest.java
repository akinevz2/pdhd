package ac.uk.sussex.kn253.prompts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.tools.WorkspaceContextTools;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Prompt spec: "List the contents of {path}. If no path is given, list the
 * current working directory."
 *
 * Covers: docs/spec/browse-directory.md
 */
@QuarkusTest
class BrowseDirectoryPromptTest {

    @Inject
    WorkspaceContextTools workspaceContextTools;

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    void listDirectoryContents_returnsChildrenOfExistingDirectory() throws Exception {
        final Path dir = Files.createTempDirectory("pdhd-browse-prompt-");
        final Path file = Files.createFile(dir.resolve("hello.txt"));
        final Path sub = Files.createDirectory(dir.resolve("subdir"));
        try {
            final String result = workspaceContextTools.listDirectoryContents(dir.toString());

            assertTrue(result.contains("Directory:"), result);
            assertTrue(result.contains("[F] hello.txt"), result);
            assertTrue(result.contains("[D] subdir"), result);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(sub);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    @Transactional
    void listFilesRecursive_returnsAllDescendantFilesInsideRegisteredProject() throws Exception {
        ProjectFolder.deleteAll();
        final Path dir = Files.createTempDirectory("pdhd-browse-recursive-");
        final Path sub = Files.createDirectory(dir.resolve("child"));
        final Path f1 = Files.createFile(dir.resolve("root.txt"));
        final Path f2 = Files.createFile(sub.resolve("nested.txt"));
        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(dir.toAbsolutePath().normalize().toString());
        project.setLoaded(true);
        project.persist();
        try {
            final String result = workspaceContextTools.listFilesRecursive(dir.toString(), 200);

            assertTrue(result.contains("root.txt"), result);
            assertTrue(result.contains("nested.txt"), result);
        } finally {
            Files.deleteIfExists(f2);
            Files.deleteIfExists(f1);
            Files.deleteIfExists(sub);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    @Transactional
    void listFilesRecursive_honoursMaxResultsLimit() throws Exception {
        ProjectFolder.deleteAll();
        final Path dir = Files.createTempDirectory("pdhd-browse-limit-");
        final List<Path> files = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            files.add(Files.createFile(dir.resolve("file" + i + ".txt")));
        }
        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(dir.toAbsolutePath().normalize().toString());
        project.setLoaded(true);
        project.persist();
        try {
            final String result = workspaceContextTools.listFilesRecursive(dir.toString(), 2);

            assertTrue(result.contains("limit 2"), result);
            // Only 2 entries should appear in the file list section
            final long listed = java.util.Arrays.stream(result.split("\n"))
                    .filter(l -> l.endsWith(".txt"))
                    .count();
            assertTrue(listed <= 2, "Expected at most 2 file entries, got: " + listed);
        } finally {
            for (final Path f : files) {
                Files.deleteIfExists(f);
            }
            Files.deleteIfExists(dir);
        }
    }

    // ── failure / edge cases ──────────────────────────────────────────────────

    @Test
    void listDirectoryContents_returnsErrorForNonExistentPath() {
        final String result = workspaceContextTools.listDirectoryContents("/tmp/pdhd-does-not-exist-xyz");

        assertTrue(result.startsWith("Error listing directory:"), result);
        assertTrue(result.contains("does not exist"), result);
    }

    @Test
    void listDirectoryContents_returnsErrorWhenPathIsAFile() throws Exception {
        final Path file = Files.createTempFile("pdhd-browse-file-", ".txt");
        try {
            final String result = workspaceContextTools.listDirectoryContents(file.toString());

            assertTrue(result.startsWith("Error listing directory:"), result);
            assertTrue(result.contains("not a directory"), result);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void listDirectoryContents_emptyDirectoryReturnsHeaderWithNoEntries() throws Exception {
        final Path dir = Files.createTempDirectory("pdhd-browse-empty-");
        try {
            final String result = workspaceContextTools.listDirectoryContents(dir.toString());

            assertTrue(result.contains("Directory:"), result);
            assertFalse(result.contains("[F]"), result);
            assertFalse(result.contains("[D]"), result);
        } finally {
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void listDirectoryContents_blankPathDefaultsToCurrentWorkingDirectory() {
        final String result = workspaceContextTools.listDirectoryContents("");

        // Should return the cwd listing, not an error
        assertTrue(result.contains("Directory:"), result);
        assertFalse(result.startsWith("Error"), result);
    }
}
