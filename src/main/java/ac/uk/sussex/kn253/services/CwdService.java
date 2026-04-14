package ac.uk.sussex.kn253.services;

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

    private final Path cwd;

    public CwdService() {
        this.cwd = Path.of(System.getProperty("user.dir"));
    }

    public Path getCurrentWorkingDirectory() {
        return cwd;
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
