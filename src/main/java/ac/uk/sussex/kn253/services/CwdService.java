package ac.uk.sussex.kn253.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ac.uk.sussex.kn253.events.CwdResolvedEvent;
import ac.uk.sussex.kn253.repository.ProjectFolder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Manages the backend working directory used by filesystem exploration APIs.
 */
@ApplicationScoped
public class CwdService {

    @Inject
    Event<CwdResolvedEvent> cwdResolvedEvents;

    private final Path startupCwd;
    private volatile Path cwd;

    public CwdService() {
        this.startupCwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        this.cwd = startupCwd;
    }

    public Path getCurrentWorkingDirectory() {
        return cwd;
    }

    public synchronized Path changeWorkingDirectory(final String directoryPath) {
        if (directoryPath == null || directoryPath.isBlank()) {
            throw new IllegalArgumentException("directoryPath is required");
        }

        final Path target = Path.of(directoryPath).toAbsolutePath().normalize();
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("Directory does not exist: " + target);
        }
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("Not a directory: " + target);
        }
        if (!isAllowedWorkingDirectory(target)) {
            throw new IllegalArgumentException("Directory is outside allowed workspace roots: " + target);
        }

        cwd = target;
        return cwd;
    }

    public boolean isFolderContained(final Path path) {
        return isContained(path) && Files.exists(path) && Files.isDirectory(path);
    }

    public boolean isFileContained(final Path path) {
        return isContained(path) && Files.exists(path) && Files.isRegularFile(path);
    }

    private boolean isContained(final Path path) {
        if (path == null) {
            return false;
        }
        try {
            final Path normalized = path.toAbsolutePath().normalize();
            if (normalized.startsWith(getCurrentWorkingDirectory().toAbsolutePath().normalize())) {
                return true;
            }
            return getOpenProjectDirectories().stream()
                    .map(Path::of)
                    .map(candidate -> candidate.toAbsolutePath().normalize())
                    .anyMatch(normalized::startsWith);
        } catch (final Exception ignored) {
            return false;
        }
    }

    private boolean isAllowedWorkingDirectory(final Path target) {
        if (target.startsWith(startupCwd)) {
            return true;
        }
        return getOpenProjectDirectories().stream()
                .map(Path::of)
                .map(candidate -> candidate.toAbsolutePath().normalize())
                .anyMatch(root -> target.startsWith(root) || root.startsWith(target));
    }

    @Transactional
    public List<String> getOpenProjectDirectories() {
        return ProjectFolder.<ProjectFolder>list("loaded", true).stream()
                .map(ProjectFolder::getDirectory)
                .sorted()
                .toList();
    }

    public ProjectFolder getCurrentProject() {
        final String cwdStr = getCurrentWorkingDirectory().toString();
        return ProjectFolder.<ProjectFolder>find("directory", cwdStr).firstResultOptional()
                .orElseGet(this::fromFileSystem);
    }

    public ProjectFolder fromFileSystem() {
        final String cwdStr = getCurrentWorkingDirectory().toString();
        final ProjectFolder projectFolder = new ProjectFolder();
        projectFolder.setDirectory(cwdStr);
        projectFolder.persist();
        cwdResolvedEvents.fire(new CwdResolvedEvent(projectFolder));
        return projectFolder;
    }

}
