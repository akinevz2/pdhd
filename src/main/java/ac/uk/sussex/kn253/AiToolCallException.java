package ac.uk.sussex.kn253;

/**
 * Unchecked exception thrown at the AI/service boundary when the model
 * produces output that cannot be parsed or dispatched as a valid tool call.
 *
 * <h2>When to throw this exception</h2>
 * <p>
 * Throw {@code AiToolCallException} whenever the fault originates in the
 * <em>model's output</em> — that is, before or during dispatch:
 * <ul>
 * <li>The model named a tool that is not registered
 * ({@code ToolRegistry.isKnownTool} returns
 * false).
 * <li>The model's response could not be parsed as valid JSON.
 * <li>The model's JSON is valid but structurally wrong for the expected schema
 * (missing required
 * keys, wrong types).
 * <li>Schema-correction retries were exhausted without the model producing a
 * conformant call.
 * </ul>
 * Always carry {@code rawModelOutput} when the model's text is available; it
 * must be logged at
 * the REST boundary and must never propagate into the service layer or
 * user-facing responses.
 *
 * <h2>When NOT to throw this exception</h2>
 * <p>
 * Do not use {@code AiToolCallException} for failures that occur <em>after</em>
 * a structurally
 * valid tool call has been dispatched. Runtime execution failures (IO errors,
 * network timeouts,
 * business-rule rejections of valid parameter values, CDI dependencies
 * unavailable) belong in
 * {@link AiToolsFailureException}.
 *
 * <h2>Usage examples</h2>
 * <ul>
 * <li>Unknown tool name:
 * {@code throw new AiToolCallException("Model called unknown tool: " + name); }
 * <li>Malformed JSON:
 * {@code throw new AiToolCallException("Malformed tool-call output from model", e, rawModelOutput); }
 * <li>Schema correction exhausted:
 * {@code throw new AiToolCallException("Schema correction retries exhausted for tool: " + name, null, rawModelOutput); }
 * </ul>
 */
public class AiToolCallException extends RuntimeException {

    private final String rawModelOutput;

    /**
     * Constructs an {@code AiToolCallException} with a message and no raw model
     * output. Use when the failure can be described without the model's original
     * text (for example, an unknown tool name detected before output is parsed).
     *
     * @param message the error message
     */
    public AiToolCallException(final String message) {
        super(message);
        this.rawModelOutput = null;
    }

    /**
     * Constructs an {@code AiToolCallException} with a message, a causative
     * exception, and the raw model output that triggered the failure.
     *
     * @param message        the error message
     * @param cause          the underlying parse or dispatch exception, or
     *                       {@code null} if there is none
     * @param rawModelOutput the exact string produced by the model; may be
     *                       {@code null} if unavailable
     */
    public AiToolCallException(final String message, final Throwable cause,
            final String rawModelOutput) {
        super(message, cause);
        this.rawModelOutput = rawModelOutput;
    }

    /**
     * Returns the raw model output captured at the point of failure, or
     * {@code null} if no output was available when the exception was constructed.
     *
     * <p>
     * This value must never be forwarded to the service layer or included in
     * user-facing responses. It is provided solely for development inspection
     * and structured logging at the REST boundary.
     *
     * @return the raw model output, or {@code null}
     */
    public String getRawModelOutput() {
        return rawModelOutput;
    }
}
