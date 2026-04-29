package ac.uk.sussex.kn253.tools;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import ac.uk.sussex.kn253.services.CwdService;
import ac.uk.sussex.kn253.services.TelemetryService;
import ac.uk.sussex.kn253.support.ToolSupport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Phase 4 tool category: repository metadata enrichment.
 *
 * <p>
 * Provides AI-accessible tools for inspecting local Git repository state
 * without requiring a GitHub-specific CLI. Each tool works on any Git
 * repository regardless of the hosting forge (GitHub, GitLab, Bitbucket,
 * Codeberg, self-hosted).
 *
 * <p>
 * All tools are telemetry-instrumented using {@link ToolSupport#MODULE_GIT}
 * and follow the same argument-validation / error-return contract as the
 * other tool classes in this package.
 */
@ApplicationScoped
public class GitMetadataTools {

    private static final Logger LOG = Logger.getLogger(GitMetadataTools.class.getName());
    private static final int GIT_TIMEOUT_SECONDS = 10;

    @Inject
    CwdService cwdService;

    @Inject
    TelemetryService telemetryService;

    // ── public tools ──────────────────────────────────────────────────────────

    @Tool(name = "get_repository_status", value = {
            "Return the short git status for the repository at the given directory.",
            "Shows modified, staged, untracked, and deleted files.",
            "If directoryPath is blank, the current working directory is used.",
            "Returns an error string if the path is not a git repository or git is unavailable."
    })
    public String getRepositoryStatus(
            @P("Repository root directory. If blank, current working directory is used.") final String directoryPath) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path repoDir = resolveRepoDir(directoryPath);
            if (repoDir == null) {
                argumentValidationFailure = true;
                errorClass = IllegalArgumentException.class.getName();
                result = "Error: path does not exist or is outside allowed workspace roots: " + directoryPath;
                return result;
            }

            final String output = runGit(repoDir, "git", "status", "--short");
            if (output == null) {
                result = "Error: git status failed – directory may not be a git repository or git is not installed.";
                errorClass = IllegalStateException.class.getName();
                return result;
            }

            result = output.isBlank()
                    ? "Repository is clean (no modified, staged, or untracked files)."
                    : "Repository status:\n" + output;
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error retrieving repository status: " + e.getMessage();
            return result;
        } finally {
            telemetryService.recordToolUse(
                    "get_repository_status",
                    ToolSupport.MODULE_GIT,
                    directoryPath,
                    result,
                    Math.max(0L, System.nanoTime() - started),
                    errorClass,
                    argumentValidationFailure);
        }
    }

    @Tool(name = "get_recent_commits", value = {
            "Return the most recent Git commits for the repository at the given directory.",
            "Shows hash, date, author, and subject for the last N commits (max 100).",
            "If directoryPath is blank, the current working directory is used.",
            "Returns an error string if git is unavailable or the path is not a repository."
    })
    public String getRecentCommits(
            @P("Repository root directory. If blank, current working directory is used.") final String directoryPath,
            @P("Number of commits to return. Defaults to 20. Min 1, max 100.") final Integer count) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path repoDir = resolveRepoDir(directoryPath);
            if (repoDir == null) {
                argumentValidationFailure = true;
                errorClass = IllegalArgumentException.class.getName();
                result = "Error: path does not exist or is outside allowed workspace roots: " + directoryPath;
                return result;
            }

            final int limit = normalizeCommitCount(count);
            final String output = runGit(repoDir, "git", "log",
                    "--date=short",
                    "--pretty=format:%h %ad %an %s",
                    "-n", String.valueOf(limit));
            if (output == null) {
                result = "Error: git log failed – directory may not be a git repository or git is not installed.";
                errorClass = IllegalStateException.class.getName();
                return result;
            }

            result = "Recent commits (last " + limit + "):\n" + output;
            return result;
        } catch (final IllegalArgumentException e) {
            argumentValidationFailure = true;
            errorClass = e.getClass().getName();
            result = "Error: " + e.getMessage();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error retrieving recent commits: " + e.getMessage();
            return result;
        } finally {
            telemetryService.recordToolUse(
                    "get_recent_commits",
                    ToolSupport.MODULE_GIT,
                    directoryPath,
                    result,
                    Math.max(0L, System.nanoTime() - started),
                    errorClass,
                    argumentValidationFailure);
        }
    }

    @Tool(name = "get_git_branches", value = {
            "List all local and remote branches for the repository at the given directory.",
            "The currently checked-out branch is prefixed with '*'.",
            "If directoryPath is blank, the current working directory is used.",
            "Returns an error string if git is unavailable or the path is not a repository."
    })
    public String getGitBranches(
            @P("Repository root directory. If blank, current working directory is used.") final String directoryPath) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path repoDir = resolveRepoDir(directoryPath);
            if (repoDir == null) {
                argumentValidationFailure = true;
                errorClass = IllegalArgumentException.class.getName();
                result = "Error: path does not exist or is outside allowed workspace roots: " + directoryPath;
                return result;
            }

            final String output = runGit(repoDir, "git", "branch", "-a");
            if (output == null) {
                result = "Error: git branch failed – directory may not be a git repository or git is not installed.";
                errorClass = IllegalStateException.class.getName();
                return result;
            }

            result = output.isBlank() ? "No branches found." : "Branches:\n" + output;
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error retrieving git branches: " + e.getMessage();
            return result;
        } finally {
            telemetryService.recordToolUse(
                    "get_git_branches",
                    ToolSupport.MODULE_GIT,
                    directoryPath,
                    result,
                    Math.max(0L, System.nanoTime() - started),
                    errorClass,
                    argumentValidationFailure);
        }
    }

    @Tool(name = "get_git_remotes", value = {
            "List all configured git remotes and their URLs for the repository at the given directory.",
            "Works with any hosting forge: GitHub, GitLab, Bitbucket, Codeberg, or self-hosted.",
            "If directoryPath is blank, the current working directory is used.",
            "Returns an error string if git is unavailable or the path is not a repository."
    })
    public String getGitRemotes(
            @P("Repository root directory. If blank, current working directory is used.") final String directoryPath) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path repoDir = resolveRepoDir(directoryPath);
            if (repoDir == null) {
                argumentValidationFailure = true;
                errorClass = IllegalArgumentException.class.getName();
                result = "Error: path does not exist or is outside allowed workspace roots: " + directoryPath;
                return result;
            }

            final String output = runGit(repoDir, "git", "remote", "-v");
            if (output == null) {
                result = "Error: git remote failed – directory may not be a git repository or git is not installed.";
                errorClass = IllegalStateException.class.getName();
                return result;
            }

            result = output.isBlank() ? "No remotes configured." : "Remotes:\n" + output;
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error retrieving git remotes: " + e.getMessage();
            return result;
        } finally {
            telemetryService.recordToolUse(
                    "get_git_remotes",
                    ToolSupport.MODULE_GIT,
                    directoryPath,
                    result,
                    Math.max(0L, System.nanoTime() - started),
                    errorClass,
                    argumentValidationFailure);
        }
    }

    @Tool(name = "get_git_diff_stat", value = {
            "Return a diffstat summary of uncommitted changes in the working tree.",
            "Shows which files have been changed and the number of insertions/deletions.",
            "If directoryPath is blank, the current working directory is used.",
            "Returns an error string if git is unavailable or the path is not a repository."
    })
    public String getGitDiffStat(
            @P("Repository root directory. If blank, current working directory is used.") final String directoryPath) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path repoDir = resolveRepoDir(directoryPath);
            if (repoDir == null) {
                argumentValidationFailure = true;
                errorClass = IllegalArgumentException.class.getName();
                result = "Error: path does not exist or is outside allowed workspace roots: " + directoryPath;
                return result;
            }

            // HEAD~..HEAD gives diff of the latest commit; HEAD gives working-tree diff
            final String staged = runGit(repoDir, "git", "diff", "--stat", "--cached");
            final String unstaged = runGit(repoDir, "git", "diff", "--stat");

            if (staged == null && unstaged == null) {
                result = "Error: git diff failed – directory may not be a git repository or git is not installed.";
                errorClass = IllegalStateException.class.getName();
                return result;
            }

            final StringBuilder out = new StringBuilder();
            final String stagedText = staged == null ? "" : staged.strip();
            final String unstagedText = unstaged == null ? "" : unstaged.strip();

            if (!stagedText.isBlank()) {
                out.append("Staged changes:\n").append(stagedText).append('\n');
            }
            if (!unstagedText.isBlank()) {
                out.append("Unstaged changes:\n").append(unstagedText);
            }
            result = out.length() == 0
                    ? "No uncommitted changes in working tree or index."
                    : out.toString().strip();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error retrieving git diff stat: " + e.getMessage();
            return result;
        } finally {
            telemetryService.recordToolUse(
                    "get_git_diff_stat",
                    ToolSupport.MODULE_GIT,
                    directoryPath,
                    result,
                    Math.max(0L, System.nanoTime() - started),
                    errorClass,
                    argumentValidationFailure);
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves and validates a repository directory.
     *
     * @return the resolved absolute path, or {@code null} if the path is invalid
     *         or outside allowed workspace roots.
     */
    private Path resolveRepoDir(final String directoryPath) {
        final Path target;
        if (directoryPath == null || directoryPath.isBlank()) {
            target = cwdService.getCurrentWorkingDirectory().toAbsolutePath().normalize();
        } else {
            target = Path.of(directoryPath).toAbsolutePath().normalize();
        }
        if (!Files.isDirectory(target)) {
            return null;
        }
        if (!cwdService.isFolderContained(target)) {
            return null;
        }
        return target;
    }

    /**
     * Runs a git command in the given directory and returns its stdout, or
     * {@code null} if the process fails, times out, or exits with a non-zero code.
     */
    String runGit(final Path directory, final String... command) {
        try {
            final Process process = new ProcessBuilder(command)
                    .directory(new File(directory.toString()))
                    .redirectErrorStream(true)
                    .start();
            final byte[] bytes = process.getInputStream().readAllBytes();
            final boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOG.fine(() -> "Git command timed out in: " + directory);
                return null;
            }
            if (process.exitValue() != 0) {
                LOG.fine(() -> "Git command exited with code " + process.exitValue() + " in: " + directory);
                return null;
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (final Exception e) {
            LOG.fine(() -> "Git command failed: " + e.getMessage());
            return null;
        }
    }

    private int normalizeCommitCount(final Integer count) {
        if (count == null) {
            return 20;
        }
        if (count < 1 || count > 100) {
            throw new IllegalArgumentException("count must be between 1 and 100, got: " + count);
        }
        return count;
    }
}
