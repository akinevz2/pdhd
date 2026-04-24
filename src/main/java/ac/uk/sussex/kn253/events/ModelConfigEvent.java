package ac.uk.sussex.kn253.events;

import ac.uk.sussex.kn253.repository.LLMSettings;

/**
 * CDI event payload for model-configuration change notifications.
 *
 * @param current the current model configuration
 */
public record ModelConfigEvent(LLMSettings current) {
}
