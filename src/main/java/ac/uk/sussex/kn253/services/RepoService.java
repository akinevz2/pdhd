package ac.uk.sussex.kn253.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import ac.uk.sussex.kn253.model.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RepoService {

    /** Matches SCP-style SSH remote URLs: {@code user@host:path/to/repo.git} */
    private static final Pattern SSH_REMOTE_PATTERN = Pattern.compile("^[^@]+@([^:]+):(.+)$");

    @jakarta.inject.Inject
    WorkingDirectoryService workingDirectoryService;

    public Path getCurrentDirectory() {
        return workingDirectoryService.getCurrentWorkingDirectory();
    }

    /**
     * Resolves the full {@link Project} for the given directory: detects the git
     * repository, queries GitHub via the {@code gh} CLI if available, and persists
     * everything to the database.
     */
    @Transactional
    public Project resolveProject(final Path path) {
        final GitRepository gitRepo = getGitRepository(path);
        final GithubRepository githubRepo = getGithubRepository(path);

        if (gitRepo != null) {
            gitRepo.persist();
        }
        if (githubRepo != null) {
            githubRepo.persist();
        }

        final Project project = new Project(null, path.toAbsolutePath().toString(), githubRepo, gitRepo);
        project.persist();
        return project;
    }

    /**
     * Queries GitHub repository metadata (name, description) for the repository at
     * {@code path} using the {@code gh} CLI. Returns {@code null} if {@code gh} is
     * not installed, the path is not a git repository, or it is not linked to
     * GitHub.
     */
    public GithubRepository getGithubRepository(final Path path) {
        if (!isGhInstalled() || !isGitRepository(path)) {
            return null;
        }
        try {
            final ProcessBuilder pb = new ProcessBuilder(
                    "gh", "repo", "view", "--json", "name,description");
            pb.directory(path.toFile());
            pb.redirectErrorStream(true);

            final Process process = pb.start();
            final String output;
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            if (process.waitFor() != 0) {
                return null;
            }
            return parseGhRepoJson(output);
        } catch (final Exception e) {
            Log.warnf("Failed to query GitHub repository info at %s: %s", path, e.getMessage());
            return null;
        }
    }

    /**
     * Returns a {@link GitRepository} populated with all fetch remotes for the
     * repository at {@code path}, or {@code null} if the path is not a git repo.
     */
    public GitRepository getGitRepository(final Path path) {
        if (!isGitRepository(path)) {
            return null;
        }
        return new GitRepository(null, getGitOrigins(path));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private GithubRepository parseGhRepoJson(final String json) {
        final String name = extractJsonStringField(json, "name");
        final String description = extractJsonStringField(json, "description");
        if (name == null) {
            return null;
        }
        return new GithubRepository(null, name, description);
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
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return process.waitFor() == 0;
        } catch (final Exception e) {
            return false;
        }
    }

    private List<Origin> getGitOrigins(final Path path) {
        try {
            final ProcessBuilder pb = new ProcessBuilder("git", "remote", "-v");
            pb.directory(path.toFile());
            pb.redirectErrorStream(true);

            final Process process = pb.start();
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                return reader.lines()
                        .filter(line -> line.endsWith("(fetch)"))
                        .map(line -> {
                            final String[] parts = line.split("\\s+");
                            return new Origin(parts[0], parseRemoteUrl(parts[1]));
                        })
                        .toList();
            }
        } catch (final Exception e) {
            return List.of();
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
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return process.waitFor() == 0;
        } catch (final Exception e) {
            return false;
        }
    }
}
