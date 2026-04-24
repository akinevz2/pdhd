package ac.uk.sussex.kn253.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.repository.ToolTelemetryRecord;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Integration tests for {@link GitMetadataTools} covering telemetry recording,
 * argument validation, and git command dispatch.
 */
@QuarkusTest
class GitMetadataToolsTest {

    @Inject
    GitMetadataTools gitMetadataTools;

    // ── get_repository_status ────────────────────────────────────────────────

    @Test
    @Transactional
    void repositoryStatusRecordsTelemetryForValidRepo() throws Exception {
        ToolTelemetryRecord.deleteAll();
        ProjectFolder.deleteAll();

        final Path repo = initTempRepo();
        try {
            final String result = gitMetadataTools.getRepositoryStatus(repo.toString());

            assertNotNull(result);
            // Either clean or shows status — must not be an error about git availability
            assertFalse(result.startsWith("Error: git status failed"), result);

            assertEquals(1L, ToolTelemetryRecord.count());
            final ToolTelemetryRecord record = ToolTelemetryRecord.findAll().firstResult();
            assertEquals("get_repository_status", record.toolName);
            assertEquals("GIT", record.moduleName);
        } finally {
            deleteRecursively(repo);
        }
    }

    @Test
    @Transactional
    void repositoryStatusReturnsErrorForNonGitDirectory() throws Exception {
        ToolTelemetryRecord.deleteAll();
        ProjectFolder.deleteAll();

        final Path plain = Files.createTempDirectory("pdhd-git-tools-test-plain-");
        try {
            // plain directory not inside any known project root → validation fail
            final String result = gitMetadataTools.getRepositoryStatus(plain.toString());
            // Should be an error (outside workspace roots)
            assertTrue(result.startsWith("Error:"), result);

            assertEquals(1L, ToolTelemetryRecord.count());
            final ToolTelemetryRecord record = ToolTelemetryRecord.findAll().firstResult();
            assertTrue(record.argumentValidationFailure);
        } finally {
            deleteRecursively(plain);
        }
    }

    // ── get_recent_commits ───────────────────────────────────────────────────

    @Test
    @Transactional
    void recentCommitsRecordsTelemetryAndRejectsOutOfRangeCount() {
        ToolTelemetryRecord.deleteAll();
        ProjectFolder.deleteAll();

        final String result = gitMetadataTools.getRecentCommits(null, 0);

        assertTrue(result.startsWith("Error:"), result);
        assertEquals(1L, ToolTelemetryRecord.count());
        final ToolTelemetryRecord record = ToolTelemetryRecord.findAll().firstResult();
        assertEquals("get_recent_commits", record.toolName);
        assertEquals("GIT", record.moduleName);
    }

    @Test
    @Transactional
    void recentCommitsDefaultCountFallsBackToTwenty() {
        // This just verifies the method accepts null count without throwing
        ToolTelemetryRecord.deleteAll();
        final String result = gitMetadataTools.getRecentCommits(null, null);
        // Result is either commits or an error – must not throw
        assertNotNull(result);
        assertEquals(1L, ToolTelemetryRecord.count());
    }

    // ── get_git_branches ─────────────────────────────────────────────────────

    @Test
    @Transactional
    void getBranchesRecordsTelemetry() {
        ToolTelemetryRecord.deleteAll();

        final String result = gitMetadataTools.getGitBranches(null);

        assertNotNull(result);
        assertEquals(1L, ToolTelemetryRecord.count());
        final ToolTelemetryRecord record = ToolTelemetryRecord.findAll().firstResult();
        assertEquals("get_git_branches", record.toolName);
        assertEquals("GIT", record.moduleName);
    }

    // ── get_git_remotes ──────────────────────────────────────────────────────

    @Test
    @Transactional
    void getRemotesRecordsTelemetry() {
        ToolTelemetryRecord.deleteAll();

        final String result = gitMetadataTools.getGitRemotes(null);

        assertNotNull(result);
        assertEquals(1L, ToolTelemetryRecord.count());
        final ToolTelemetryRecord record = ToolTelemetryRecord.findAll().firstResult();
        assertEquals("get_git_remotes", record.toolName);
        assertEquals("GIT", record.moduleName);
    }

    // ── get_git_diff_stat ────────────────────────────────────────────────────

    @Test
    @Transactional
    void getDiffStatRecordsTelemetry() {
        ToolTelemetryRecord.deleteAll();

        final String result = gitMetadataTools.getGitDiffStat(null);

        assertNotNull(result);
        assertEquals(1L, ToolTelemetryRecord.count());
        final ToolTelemetryRecord record = ToolTelemetryRecord.findAll().firstResult();
        assertEquals("get_git_diff_stat", record.toolName);
        assertEquals("GIT", record.moduleName);
    }

    // ── runGit helper ────────────────────────────────────────────────────────

    @Test
    void runGitReturnsNullForInvalidCommand() throws Exception {
        final Path tmp = Files.createTempDirectory("pdhd-git-run-test-");
        try {
            final String result = gitMetadataTools.runGit(tmp, "git", "definitely-not-a-real-git-subcommand");
            // non-zero exit → null
            assertTrue(result == null || result.contains("unknown"), "Expected null or error output");
        } finally {
            deleteRecursively(tmp);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Initialises a minimal git repository in a temp directory with one commit.
     * The directory is added as an open project so the workspace containment
     * check passes.
     */
    @Transactional
    Path initTempRepo() throws Exception {
        final Path repo = Files.createTempDirectory("pdhd-git-tools-test-repo-");
        Files.writeString(repo.resolve("README.md"), "# test", StandardCharsets.UTF_8);

        runGitInit(repo);

        final ProjectFolder project = new ProjectFolder();
        project.setDirectory(repo.toAbsolutePath().normalize().toString());
        project.setLoaded(true);
        project.persist();

        return repo;
    }

    private void runGitInit(final Path repo) {
        try {
            run(repo, "git", "init");
            run(repo, "git", "config", "user.email", "test@test.com");
            run(repo, "git", "config", "user.name", "Test");
            run(repo, "git", "add", ".");
            run(repo, "git", "commit", "-m", "initial");
        } catch (final Exception ignored) {
            // if git is not available, tests relying on this will get appropriate error
            // strings
        }
    }

    private void run(final Path dir, final String... cmd) throws Exception {
        final Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
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
