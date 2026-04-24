package ac.uk.sussex.kn253.events;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.services.CwdService;

/**
 * CDI event emitted when {@link CwdService} resolves the current working
 * directory to a {@link ProjectFolder} record.
 *
 * @param projectFolder the resolved project folder
 */
public record CwdResolvedEvent(ProjectFolder projectFolder) {
}
