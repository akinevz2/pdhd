package ac.uk.sussex.kn253.services;

import java.nio.file.Path;

import ac.uk.sussex.kn253.events.CwdResolvedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Assistant runtime working directory synced from backend cwd events.
 */
@ApplicationScoped
public class AssistantWorkingDirectoryService {

    private volatile String currentWorkingDirectory = Path.of("").toAbsolutePath().normalize().toString();

    public String getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }

    public void onCwdResolved(final @Observes CwdResolvedEvent event) {
        if (event == null || event.resolvedPath() == null || event.resolvedPath().isBlank()) {
            return;
        }
        currentWorkingDirectory = event.resolvedPath();
    }
}