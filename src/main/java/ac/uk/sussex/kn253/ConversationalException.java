package ac.uk.sussex.kn253;

import java.util.*;

/**
 * Unchecked exception used by the Service layer to gather and propagate
 * multiple
 * failures encountered during tool-calling and conversational workflows.
 *
 * <p>
 * This exception allows the accumulation of multiple causative exceptions,
 * enabling the caller to handle grouped failures gracefully rather than
 * stopping at the first error.
 *
 * <p>
 * Usage examples:
 * <ul>
 * <li>Single string message:
 * {@code throw new ConversationalException("Tool call failed"); }
 * <li>Single exception:
 * {@code throw new ConversationalException(originalException); }
 * <li>Multiple exceptions:
 * {@code throw new ConversationalException(ex1, ex2, ex3); }
 * <li>Message with exceptions:
 * {@code throw new ConversationalException("Multiple tool failures", ex1, ex2); }
 * </ul>
 */
public class ConversationalException extends RuntimeException {

    private final List<Throwable> causes;

    /**
     * Constructs a ConversationalException with a message and no additional causes.
     *
     * @param message the error message
     */
    public ConversationalException(final String message) {
        super(message);
        this.causes = Collections.emptyList();
    }

    /**
     * Constructs a ConversationalException from a single exception.
     *
     * @param cause the causative exception
     */
    public ConversationalException(final Throwable cause) {
        super(cause.getMessage(), cause);
        this.causes = Collections.singletonList(cause);
    }

    /**
     * Constructs a ConversationalException with a message and a single exception.
     *
     * @param message the error message
     * @param cause   the causative exception
     */
    public ConversationalException(final String message, final Throwable cause) {
        super(message, cause);
        this.causes = Collections.singletonList(cause);
    }

    /**
     * Constructs a ConversationalException from multiple exceptions.
     *
     * @param causes varargs of causative exceptions
     */
    public ConversationalException(final Throwable... causes) {
        super(buildMessageFromExceptions(causes));
        this.causes = causes != null && causes.length > 0
                ? Arrays.asList(causes)
                : Collections.emptyList();
        if (causes != null && causes.length > 0) {
            this.initCause(causes[0]);
        }
    }

    /**
     * Constructs a ConversationalException with a message and multiple exceptions.
     *
     * @param message the error message
     * @param causes  varargs of causative exceptions
     */
    public ConversationalException(final String message, final Throwable... causes) {
        super(message, causes != null && causes.length > 0 ? causes[0] : null);
        this.causes = causes != null && causes.length > 0
                ? Arrays.asList(causes)
                : Collections.emptyList();
    }

    /**
     * Copy constructor that combines an existing ConversationalException with an
     * additional trailing exception.
     *
     * <p>
     * Creates a new exception with all causes from the original plus the trailing
     * exception appended, preserving the accumulated failure history while adding
     * a new failure.
     *
     * @param original the existing ConversationalException to copy causes from
     * @param trailing the additional exception to append to the list
     */
    public ConversationalException(final ConversationalException original, final Throwable trailing) {
        super(original.getMessage(), original.getCause());
        final List<Throwable> combined = new ArrayList<>(original.causes);
        if (trailing != null) {
            combined.add(trailing);
        }
        this.causes = combined;
    }

    /**
     * Returns an immutable list of all causative exceptions accumulated in this
     * exception.
     *
     * @return list of exceptions; empty if none were provided
     */
    public List<Throwable> getCauses() {
        return Collections.unmodifiableList(causes);
    }

    /**
     * Returns the number of accumulated exceptions.
     *
     * @return count of causative exceptions
     */
    public int getCauseCount() {
        return causes.size();
    }

    /**
     * Checks if this exception contains any accumulated causes.
     *
     * @return true if at least one cause is present
     */
    public boolean hasCauses() {
        return !causes.isEmpty();
    }

    /**
     * Returns a detailed string representation including all accumulated causes.
     *
     * @return formatted exception details
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(super.toString());
        if (!causes.isEmpty()) {
            sb.append("\nAccumulated causes (").append(causes.size()).append("):\n");
            for (int i = 0; i < causes.size(); i++) {
                final Throwable cause = causes.get(i);
                sb.append("  [").append(i + 1).append("] ").append(cause.getClass().getSimpleName())
                        .append(": ").append(cause.getMessage()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Builds a summary message from an array of exceptions.
     *
     * @param exceptions array of exceptions
     * @return formatted message summarizing the exceptions
     * @throws AssertionError if exceptions is null or empty, indicating a critical
     *                        programming error that requires immediate application
     *                        termination
     */
    private static String buildMessageFromExceptions(final Throwable[] exceptions) {
        if (exceptions == null || exceptions.length == 0) {
            throw new AssertionError(
                    "CRITICAL: ConversationalException constructed with no causative exceptions. "
                            + "This indicates a programming error in the conversational/tool-calling layer. "
                            + "Application must terminate immediately.");
        }
        if (exceptions.length == 1) {
            return "Conversational failure: " + exceptions[0].getMessage();
        }
        final StringBuilder sb = new StringBuilder("Conversational failure with ")
                .append(exceptions.length).append(" error(s): ");
        for (int i = 0; i < Math.min(exceptions.length, 3); i++) {
            if (i > 0)
                sb.append("; ");
            sb.append(exceptions[i].getMessage());
        }
        if (exceptions.length > 3) {
            sb.append("; and ").append(exceptions.length - 3).append(" more");
        }
        return sb.toString();
    }
}
