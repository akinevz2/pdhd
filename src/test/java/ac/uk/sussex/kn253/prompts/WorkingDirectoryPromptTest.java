package ac.uk.sussex.kn253.prompts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.tools.WorkspaceContextTools;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prompt spec: "What is the current working directory?" /
 * "Change the working directory to {path}."
 *
 * Covers: docs/spec/working-directory.md
 */
@QuarkusTest
class WorkingDirectoryPromptTest {

    @Inject
    WorkspaceContextTools workspaceContextTools;

    // ── get cwd ───────────────────────────────────────────────────────────────

    @Test
    void getCurrentWorkingDirectory_returnsAbsolutePath() {
        final String result = workspaceContextTools.getCurrentWorkingDirectory();

        assertFalse(result.startsWith("Error"), result);
        assertTrue(result.startsWith("/"), "Expected absolute path, got: " + result);
        assertFalse(result.endsWith("/") && result.length() > 1, result);
        assertFalse(result.contains("/./") || result.contains("/../"), result);
    }

    @Test
    void getCurrentWorkingDirectory_doesNotReturnProxyClassName() {
        final String result = workspaceContextTools.getCurrentWorkingDirectory();

        assertFalse(result.startsWith("Error"), result);
        assertFalse(result.contains("$Proxy"), result);
        assertFalse(result.contains("_ClientProxy"), result);
    }

    // ── change cwd ────────────────────────────────────────────────────────────

    @Test
    void changeWorkingDirectory_succeedsForExistingDirectoryInsideWorkspaceRoot() throws Exception {
        final String currentCwd = workspaceContextTools.getCurrentWorkingDirectory();
        final Path current = Path.of(currentCwd);

        Optional<Path> childOpt;
        try (var stream = Files.list(current)) {
            childOpt = stream.filter(Files::isDirectory).findFirst();
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(childOpt.isPresent(),
                "No child directory found in cwd; skipping test.");
        final Path target = childOpt.get();

        final String result = workspaceContextTools.changeWorkingDirectory(target.toString());

        assertTrue(result.contains("changed to"), result);
        assertTrue(result.contains(target.toAbsolutePath().normalize().toString()), result);

        workspaceContextTools.changeWorkingDirectory(currentCwd);
    }

    @Test
    void changeWorkingDirectory_returnsErrorForNonExistentPath() {
        final String result = workspaceContextTools.changeWorkingDirectory(
                "/tmp/pdhd-cwd-does-not-exist-xyz-abc");

        assertTrue(result.startsWith("Error"), result);
    }

    @Test
    void changeWorkingDirectory_returnsErrorForFilePath() throws Exception {
        final Path file = Files.createTempFile("pdhd-cwd-file-", ".txt");
        try {
            final String result = workspaceContextTools.changeWorkingDirectory(file.toString());

            assertTrue(result.startsWith("Error"), result);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
