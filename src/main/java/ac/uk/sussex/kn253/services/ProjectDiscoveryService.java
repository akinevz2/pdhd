package ac.uk.sussex.kn253.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import ac.uk.sussex.kn253.model.Project;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Service responsible for discovering and registering Git projects on the file
 * system as {@link Project} database records.
 *
 * <p>
 * Discovery walks from the current working directory up to
 * {@link #SCAN_DEPTH} levels deep looking for {@code .git} directories, then
 * ensures each found parent path has a corresponding {@link Project} entity in
 * the database. The current working directory itself is always checked
 * regardless of whether a {@code .git} directory is present.
 */
@ApplicationScoped
public class ProjectDiscoveryService {

    private static final int SCAN_DEPTH = 4;

    private final WorkingDirectoryService workingDirectoryService;
    private final RepoService repoService;

    /**
     * CDI constructor.
     *
     * @param workingDirectoryService provides the current working directory.
     * @param repoService             resolves and persists project metadata.
     */
    @Inject
    public ProjectDiscoveryService(
            final WorkingDirectoryService workingDirectoryService,
            final RepoService repoService) {
        this.workingDirectoryService = workingDirectoryService;
        this.repoService = repoService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scans the current working directory tree and ensures that every
     * discovered Git project root (and the CWD itself) has a {@link Project}
     * record in the database.
     *
     * <p>
     * This method is transactional – it must be called within or will start
     * a transaction. Any {@link IOException} from the filesystem walk is
     * silently swallowed so that the API remains resilient.
     * 
     * @throws IOException
     */
    @Transactional
    public void discoverFromCwd() throws IOException {
        final Path cwd = workingDirectoryService.getCurrentWorkingDirectory();

        // Always ensure the CWD itself is registered; explicit failures should
        // propagate.
        ensureExists(cwd, false);

        // Walk the tree looking for .git directories.
        try (Stream<Path> stream = Files.walk(cwd, SCAN_DEPTH)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> path.getFileName().toString().equals(".git"))
                    .map(Path::getParent)
                    .filter(Objects::nonNull)
                    // Per-folder discovery should remain best-effort and silent.
                    .forEach(path -> ensureExists(path, true));
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Ensures a {@link Project} record exists for {@code directoryPath}.
     * If none is found, {@link RepoService#resolveProject(Path)} is called to
     * create and persist one.
     *
     * @param directoryPath the absolute project root directory.
     */
    private void ensureExists(final Path directoryPath, final boolean silentOnNotGit) {
        final String directory = directoryPath.toAbsolutePath().normalize().toString();
        final Project existing = Project.find("directory", directory).firstResult();
        if (existing == null) {
            try {
                repoService.resolveProject(Path.of(directory));
            } catch (final ac.uk.sussex.kn253.api.NotAGitRepositoryException e) {
                if (silentOnNotGit) {
                    Log.debugf("Skipping non-git directory during discovery: %s", directory);
                    return;
                }
                throw e;
            } catch (final Exception e) {
                Log.warnf("Skipping project discovery for %s: %s", directory, e.getMessage());
            }
        }
    }
}
