package ac.uk.sussex.kn253.services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import ac.uk.sussex.kn253.repository.*;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Handles Git operations: retrieving remote URLs, commit logs, and executing
 * git commands.
 */
@ApplicationScoped
public class GitOperationService {

    private static final Logger LOG = Logger.getLogger(GitOperationService.class.getName());

    public String resolveRemoteUrl(final ProjectFolder project) {
        if (project.getGithubRepository() != null && project.getGithubRepository().getRepoUrl() != null
                && !project.getGithubRepository().getRepoUrl().isBlank()) {
            return project.getGithubRepository().getRepoUrl();
        }
        final GitFolder gitRepository = project.getGitRepository();
        if (gitRepository != null && gitRepository.getOrigins() != null) {
            for (final Origin origin : gitRepository.getOrigins()) {
                if (origin != null && origin.isGithub() && origin.getUrl() != null) {
                    return origin.getUrl().toString();
                }
            }
        }
        final String remote = runGitCommand(project, "git", "config", "--get", "remote.origin.url");
        if (remote == null || remote.isBlank()) {
            return null;
        }
        return remote.contains("github.com") ? remote : null;
    }

    public String loadCommitLogText(final ProjectFolder project) {
        final String output = runGitCommand(project, "git", "log", "--date=short",
                "--pretty=format:%h %ad %an %s", "-n", "30");
        return output == null ? "" : output;
    }

    public String runGitCommand(final ProjectFolder project, final String... command) {
        final String directory = project.getDirectory();
        if (directory == null || directory.isBlank()) {
            return null;
        }
        if (!Files.exists(Path.of(directory))) {
            return null;
        }
        try {
            final Process process = new ProcessBuilder(command)
                    .directory(new java.io.File(directory))
                    .redirectErrorStream(true)
                    .start();
            final byte[] bytes = process.getInputStream().readAllBytes();
            final boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                if (!finished) {
                    process.destroyForcibly();
                }
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (final Exception e) {
            LOG.fine(() -> "Git command failed: " + e.getMessage());
            return null;
        }
    }
}
