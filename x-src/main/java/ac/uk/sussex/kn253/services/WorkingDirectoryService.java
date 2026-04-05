package ac.uk.sussex.kn253.services;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class WorkingDirectoryService {

    private final static String INIT_DIR = System.getProperty("user.dir");
    private volatile Path currentWorkingDirectory = Path.of(INIT_DIR).toAbsolutePath().normalize();

    public synchronized Path getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }

    public synchronized boolean cd(final Path rel) {
        final var newPath = currentWorkingDirectory.resolve(rel);
        try {
            final var newRealPath = newPath.toRealPath();
            if (Files.isDirectory(newRealPath)) {
                synchronized (this) {
                    currentWorkingDirectory = newRealPath;
                }
                return true;
            }
        } catch (final Exception e) {
        }
        return false;
    }

}
