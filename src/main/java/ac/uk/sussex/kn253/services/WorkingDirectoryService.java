package ac.uk.sussex.kn253.services;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WorkingDirectoryService {

    private Path currentWorkingDirectory = Path.of("").toAbsolutePath().normalize();

    public synchronized Path getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }

    public synchronized Path resolveAgainstCurrent(final String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return currentWorkingDirectory;
        }
        final Path maybeRelative = Path.of(rawPath.trim());
        return (maybeRelative.isAbsolute() ? maybeRelative : currentWorkingDirectory.resolve(maybeRelative))
                .toAbsolutePath()
                .normalize();
    }

    public synchronized Path navigateTo(final String rawPath) {
        final Path target = resolveAgainstCurrent(rawPath);
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("Path does not exist: " + target);
        }
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("Not a directory: " + target);
        }
        currentWorkingDirectory = target;
        return currentWorkingDirectory;
    }
}
