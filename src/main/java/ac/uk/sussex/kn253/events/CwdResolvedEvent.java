package ac.uk.sussex.kn253.events;

import ac.uk.sussex.kn253.services.CwdService;

/**
 * CDI event emitted when {@link CwdService} resolves a directory path.
 *
 * @param requestedPath raw path passed by the caller
 * @param resolvedPath  normalized absolute directory path
 */
public record CwdResolvedEvent(String requestedPath, String resolvedPath) {
}
