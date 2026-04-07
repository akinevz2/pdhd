package ac.uk.sussex.kn253.events;

import ac.uk.sussex.kn253.repository.LLMSettings;

/**
 * CDI event published when model configuration changes.
 *
 * @param current the current model configuration
 */
public record ModelConfigEvent(LLMSettings current) {
}
