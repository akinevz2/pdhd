package ac.uk.sussex.kn253.events;

/**
 * CDI event requesting an assistant chat repaint.
 *
 * @param reason description of what triggered the repaint request
 */
public record ChatRepaintEvent(String reason) {
}
