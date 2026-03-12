package ac.uk.sussex.kn253.services;

import java.nio.file.Path;
import java.util.List;

import ac.uk.sussex.kn253.model.*;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RepoService {

    private static final String CURRENT_PATH = ".";

    public Path getCurrentDirectory() {
        return Path.of(CURRENT_PATH).toAbsolutePath();
    }

    public GithubRepository getRepositoryInfo(final Path path) {
        // Placeholder for actual implementation to read repository information
        // In a real application, this would involve reading from a file or using a
        // library to parse the repository details
        if (isGitRepository(getCurrentDirectory())) {
            final var origins = getGitOrigins(path);
            if (!origins.isEmpty()) {

                return new GithubRepository(repoName, repoDescription);
            }
        }
        return null;
    }

    public GitRepository getGitRepository(final Path path) {
        // Placeholder for actual implementation to read the .git directory and create a
        // GitRepository object
        // In a real application, this would involve checking for the existence of the
        // .git directory and reading its contents
        if (isGitRepository(path)) {
            final var origins = getGitOrigins(path);
            return new GitRepository(origins);
        }
        return null;
    }

    private List<Origin> getGitOrigins(final Path path) {
        try {
            final ProcessBuilder pb = new ProcessBuilder("git", "remote", "-v");
            pb.directory(path.toFile());
            pb.redirectErrorStream(true);

            final Process process = pb.start();
            try (final var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                return reader.lines()
                        .filter(line -> line.endsWith("(fetch)"))
                        .map(line -> {
                            final String[] parts = line.split("\\s+");
                            final String name = parts[0];
                            final String url = parts[1];
                            try {
                                return new Origin(name, java.net.URI.create(url).toURL());
                            } catch (final java.net.MalformedURLException e) {
                                return null; // Skip malformed URLs
                            }
                        })
                        .filter(origin -> origin != null)
                        .toList();
            }
        } catch (final Exception e) {
            return List.of();
        }
    }

    private boolean isGitRepository(final Path path) {
        try {
            final ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--git-dir");
            pb.directory(path.toFile());
            pb.redirectErrorStream(true);

            final Process process = pb.start();
            final int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (final Exception e) {
            return false;
        }
    }
}
