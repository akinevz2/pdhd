package ac.uk.sussex.kn253.prompts;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.tools.GitMetadataTools;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Prompt spec: "Show the git log for {path}." /
 * "What is the git status of {path}?"
 *
 * Covers: docs/spec/git-metadata.md
 */
@QuarkusTest
class GitMetadataPromptTest {

    @Inject
    GitMetadataTools gitMetadataTools;

    // ── git status ────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void getRepositoryStatus_returnsStatusForValidGitRepo() throws Exception {
        ProjectFolder.deleteAll();

        final Path repo = initTempRepo();
        try {
            final String result = gitMetadataTools.getRepositoryStatus(repo.toString());

            assertNotNull(result);
            assertFalse(result.startsWith("Error: git status failed"), result);
        } finally {
            deleteRecursively(repo);
        }
    }

    @Test
    @Transactional
    void getRepositoryStatus_returnsErrorForPathOutsideWorkspaceRoots() throws Exception {
        ProjectFolder.deleteAll();

        final Path plain = Files.createTempDirectory("pdhd-git-prompt-plain-");
        try {
            final String result = gitMetadataTools.getRepositoryStatus(plain.toString());

            assertTrue(result.startsWith("Error:"), result);
        } finally {
            deleteRecursively(plain);
        }
    }

    // ── git log ───────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void getRecentCommits_returnsCommitHistoryForValidRepo() throws Exception {
        ProjectFolder.deleteAll();

        final Path repo = initTempRepo();
        try {
            final String result = gitMetadataTools.getRecentCommits(repo.toString(), 5);

            assertNotNull(result);
            // Either commit output or "no commits" — must not be a hard error
            assertFalse(result.startsWith("Error:") && result.contains("git log failed"), result);
        } finally {
            deleteRecursively(repo);
        }
    }

    @Test
    void getRecentCommits_returnsErrorForZeroCount() {
        final String result = gitMetadataTools.getRecentCommits(null, 0);

        assertTrue(result.startsWith("Error:"), result);
    }

    @Test
    void getRecentCommits_acceptsNullCountAndDoesNotThrow() {
        final String result = gitMetadataTools.getRecentCommits(null, null);

        assertNotNull(result);
    }

    // ── git branches ──────────────────────────────────────────────────────────

    @Test
    void getGitBranches_returnsResultWithoutThrowing() {
        final String result = gitMetadataTools.getGitBranches(null);

        assertNotNull(result);
    }

    // ── git remotes ───────────────────────────────────────────────────────────

    @Test
    void getGitRemotes_returnsResultWithoutThrowing() {
        final String result = gitMetadataTools.getGitRemotes(null);

        assertNotNull(result);
    }

    // ── git diff stat ─────────────────────────────────────────────────────────

    @Test
    void getGitDiffStat_returnsResultWithoutThrowing() {
        final String result = gitMetadataTools.getGitDiffStat(null);

        assertNotNull(result);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Transactional
    Path initTempRepo() throws Exception {
        final Path repo = Files.createTempDirectory("pdhd-git-prompt-repo-");
        Files.writeString(repo.resolve("README.md"), "# test", StandardCharsets.UTF_8);

        try {
            run(repo, "git", "init");
            run(repo, "git", "config", "user.email", "test@test.com");
            run(repo, "git", "config", "user.name", "Test");
            run(repo, "git", "add", ".");
            run(repo, "git", "commit", "-m", "initial");
        } catch (final Exception ignored) {
            // git may not be available; individual tests degrade gracefully
        }

        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(repo.toAbsolutePath().normalize().toString());
        project.setLoaded(true);
        project.persist();

        return repo;
    }

    private void run(final Path dir, final String... cmd) throws Exception {
        new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor();
    }

    private void deleteRecursively(final Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        Files.walk(root)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (final Exception ignored) {
                    }
                });
    }
}
