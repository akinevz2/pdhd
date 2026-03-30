package ac.uk.sussex.kn253.services;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;

import ac.uk.sussex.kn253.api.*;
import ac.uk.sussex.kn253.model.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Service for resolving and persisting project metadata from Git and GitHub.
 *
 * <p>
 * Given a directory path, {@link #resolveProject(Path)} detects the local
 * {@link GitRepository} (via {@code git remote -v}), queries GitHub metadata
 * (via the {@code gh} CLI when available), and persists a {@link Project}
 * record to the database.
 */
@ApplicationScoped
public class RepoService {

    /** Matches SCP-style SSH remote URLs: {@code user@host:path/to/repo.git} */
    private static final Pattern SSH_REMOTE_PATTERN = Pattern.compile("^[^@]+@([^:]+):(.+)$");
    private static final int PROCESS_TIMEOUT_SECONDS = 6;

    @jakarta.inject.Inject
    WorkingDirectoryService workingDirectoryService;

    public Path getCurrentDirectory() {
        return workingDirectoryService.getCurrentWorkingDirectory();
    }

    /**
     * Resolves the full {@link Project} for the given directory: detects the git
     * repository, optionally queries GitHub metadata, and persists the project.
     *
     * <p>
     * <ul>
     * <li>Non-git paths are rejected via {@link NotAGitRepositoryException}.</li>
     * <li>Git paths always produce a persisted {@link Project} with a persisted
     * {@link GitRepository}.</li>
     * <li>GitHub metadata is best-effort: if the repo is not linked to GitHub,
     * or GitHub metadata cannot be resolved, the project is still persisted with
     * {@code githubRepo == null}.</li>
     * </ul>
     * 
     * @throws ResolutionException if there are errors resolving the project
     */
    @Transactional
    public Project resolveProject(final Path path) throws ResolutionException {
        final String directory = path.toAbsolutePath().normalize().toString();
        final Project existing = Project.find("directory", directory).firstResult();
        if (existing != null) {
            return existing;
        }

        final GitRepository gitRepo = getGitRepository(path);
        GithubRepository githubRepo = null;
        try {
            githubRepo = getGithubRepository(path);
        } catch (final IOException | InterruptedException forwarded) {
            throw new ResolutionException(path, "GitHub repository metadata", forwarded);
        }

        gitRepo.persist();
        if (githubRepo != null) {
            githubRepo.persist();
        }

        final Project project = new Project(null, directory, githubRepo, gitRepo);
        project.persist();
        return project;
    }

    /**
     * Queries GitHub repository metadata (name, description) for the repository at
     * {@code path} using the {@code gh} CLI.
     *
     * <p>
     * Intended behaviour (strict getter):
     * <ul>
     * <li>Throws {@link NotAGitRepositoryException} when {@code path} is not a git
     * repository.</li>
     * <li>Throws {@link NotAGithubRepositoryException} when the git repo has no
     * GitHub remotes, the {@code gh} CLI is unavailable, or metadata lookup
     * fails.</li>
     * <li>Returns a non-null {@link GithubRepository} on success.</li>
     * </ul>
     * 
     * @throws InterruptedException
     * @throws IOException
     */
    public GithubRepository getGithubRepository(final Path path) throws IOException, InterruptedException {
        if (!isGitRepository(path)) {
            throw new NotAGitRepositoryException(path);
        }
        if (!isGhInstalled()) {
            throw new NotAGithubRepositoryException(path, NotAGithubRepositoryException.GITHUB_CLI_MISSING);
        }
        final var gitOrigins = getGitOrigins(path);
        if (gitOrigins.isEmpty()) {
            throw new NotAGithubRepositoryException(path, "No remotes found");
        }
        if (gitOrigins.stream().noneMatch(Origin::isGithub)) {
            throw new NotAGithubRepositoryException(path, "No GitHub remotes found");
        }
        return queryGithubMetadata(path);
    }

    /**
     * Returns a {@link GitRepository} populated with all fetch remotes for the
     * repository at {@code path}.
     *
     * @throws NotAGitRepositoryException if the path is not a git repository
     */
    public @NonNull GitRepository getGitRepository(final Path path) {
        if (!isGitRepository(path)) {
            throw new NotAGitRepositoryException(path);
        }
        return new GitRepository(null, getGitOrigins(path));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private GithubRepository parseGhRepoJson(final Process process, final Path path)
            throws IOException, InterruptedException {
        try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            final String output = reader.lines().collect(Collectors.joining("\n"));

            if (!waitForProcess(process, "gh repo view", path) || process.exitValue() != 0) {
                throw new NotAGithubRepositoryException(path, "gh repo view command failed");
            }

            final String name = extractJsonStringField(output, "name");
            final String description = extractJsonStringField(output, "description");
            if (name == null) {
                throw new NotAGithubRepositoryException(path, "Failed to parse GitHub repository metadata");
            }
            return new GithubRepository(null, name, description);
        }
    }

    /**
     * Queries GitHub repository metadata by executing {@code gh repo view} in the
     * given directory.
     *
     * @return a {@link GithubRepository} with name and description, or {@code null}
     *         if the repository is not a GitHub repository
     * @throws IOException          if there are errors executing the {@code gh}
     *                              command or
     *                              parsing its output
     * @throws InterruptedException if the {@code gh} command is interrupted during
     *                              execution
     */
    private GithubRepository queryGithubMetadata(final Path path) throws ResolutionException {
        try {
            final ProcessBuilder pb = new ProcessBuilder("gh", "repo", "view", "--json", "name,description");
            pb.directory(path.toFile());
            pb.redirectErrorStream(true);

            final Process process = pb.start();
            return parseGhRepoJson(process, path);
        } catch (final IOException | InterruptedException e) {
            throw new ResolutionException(path, "gh repo view command failed", e);
        }
    }

    /**
     * Minimal JSON string-field extractor. Handles basic escape sequences
     * ({@code \"}, {@code \\}, {@code \n}, {@code \t}).
     */
    private String extractJsonStringField(final String json, final String field) {
        final Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        final Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
    }

    /** Returns {@code true} if the {@code gh} CLI is present and executable. */
    private boolean isGhInstalled() {
        try {
            final Process process = new ProcessBuilder("gh", "--version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            return waitForProcess(process, "gh --version", null) && process.exitValue() == 0;
        } catch (final Exception e) {
            return false;
        }
    }

    private List<@NonNull Origin> getGitOrigins(final Path path) {
        try {
            final ProcessBuilder pb = new ProcessBuilder("git", "remote", "-v");
            pb.directory(path.toFile());
            pb.redirectErrorStream(true);

            final Process process = pb.start();
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final List<@NonNull Origin> origins = reader.lines()
                        .filter(line -> line.endsWith("(fetch)"))
                        .map(line -> {
                            final String[] parts = line.split("\\s+");
                            return new Origin(parts[0], parseRemoteUrl(parts[1]));
                        })
                        .toList();
                final boolean processSuccess = waitForProcess(process, "git remote -v", path)
                        && process.exitValue() == 0;
                if (!processSuccess) {
                    return List.of();
                }
                return origins;
            }
        } catch (final IOException | InterruptedException e) {
            throw new ResolutionException(path, "git remote -v command failed", e);
        }
    }

    /**
     * Parses a git remote URL string into a {@link URL}. Handles standard URLs
     * ({@code https://}, {@code git://}) and SCP-style SSH remotes
     * ({@code git@github.com:owner/repo.git}), converting the latter to their
     * {@code https://} equivalent.
     *
     * @return the parsed {@link URL}, or {@code null} if the string cannot be
     *         parsed
     */
    private URL parseRemoteUrl(final String urlStr) {
        try {
            return URI.create(urlStr).toURL();
        } catch (final Exception e) {
            // Fall back to SCP-style SSH URL: git@github.com:owner/repo.git
            final Matcher m = SSH_REMOTE_PATTERN.matcher(urlStr);
            if (m.matches()) {
                try {
                    return URI.create("https://" + m.group(1) + "/" + m.group(2)).toURL();
                } catch (final Exception ignored) {
                    // not parseable as a URL
                }
            }
            return null;
        }
    }

    private boolean isGitRepository(final Path path) {
        try {
            final ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--git-dir");
            pb.directory(path.toFile());
            pb.redirectErrorStream(true);

            final Process process = pb.start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            return waitForProcess(process, "git rev-parse --git-dir", path) && process.exitValue() == 0;
        } catch (final Exception e) {
            return false;
        }
    }

    private boolean waitForProcess(final Process process, final String command, final Path path)
            throws InterruptedException {
        final boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (finished) {
            return true;
        }
        process.destroyForcibly();
        if (path != null) {
            Log.warnf("Command timed out after %ds (%s) in %s", PROCESS_TIMEOUT_SECONDS, command, path);
        } else {
            Log.warnf("Command timed out after %ds (%s)", PROCESS_TIMEOUT_SECONDS, command);
        }
        return false;
    }
}
