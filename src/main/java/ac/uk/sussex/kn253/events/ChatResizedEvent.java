package ac.uk.sussex.kn253.events;

/**
 * CDI event emitted when the assistant chat terminal dimensions change.
 *
 * @param previousWidth  terminal width before resize
 * @param previousHeight terminal height before resize
 * @param width          current terminal width
 * @param height         current terminal height
 */
public record ChatResizedEvent(int previousWidth, int previousHeight, int width, int height) {
}
