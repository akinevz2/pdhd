package ac.uk.sussex.kn253;

import java.util.*;

/**
 * Exception that encapsulates any failure mode that can occur in the
 * tool-calling mechanism, providing categorized, actionable error information
 * for both immediate handling and accumulated failure reporting.
 *
 * <h2>When to throw this exception</h2>
 * <p>
 * Throw {@code AiToolsFailureException} whenever the fault originates in
 * <em>runtime execution after a structurally valid tool call has been
 * dispatched</em>:
 * <ul>
 * <li>A CDI bean or service dependency required for execution is unavailable
 * ({@link FailureMode#TOOL_NOT_FOUND}).
 * <li>A service-layer business rule rejects a parameter value that was
 * structurally valid at the parse boundary — for example, {@code maxResults=50}
 * when the service only accepts 1–10 ({@link FailureMode#ARGUMENT_VALIDATION}).
 * <li>An I/O, network, security, or timeout failure during tool execution.
 * </ul>
 * Multiple failures may be accumulated across sequential tool calls by using
 * the
 * copy-constructor before propagating to the caller.
 *
 * <h2>When NOT to throw this exception</h2>
 * <p>
 * Do not use {@code AiToolsFailureException} for failures that originate in the
 * <em>model's output</em> — that is, before or during dispatch. If the model
 * named an unknown tool, produced malformed JSON, sent wrong argument types, or
 * exhausted schema-correction retries, throw {@link AiToolCallException}
 * instead.
 * That exception carries {@code rawModelOutput} and maps to
 * {@code AI_LAYER_FAILURE} at the REST boundary, while this exception maps to
 * {@code SERVICE_LAYER_FAILURE}.
 *
 * <h2>Failure modes</h2>
 * <p>
 * See {@link FailureMode} for the full list. Key modes:
 * <ul>
 * <li>{@link FailureMode#TOOL_NOT_FOUND} — CDI/service dependency unavailable
 * at
 * execution time (not: model named an unknown tool)
 * <li>{@link FailureMode#ARGUMENT_VALIDATION} — business rule rejects a
 * structurally valid value (not: schema/type mismatch from the model)
 * <li>{@link FailureMode#FILE_ACCESS_DENIED} — access outside allowed roots or
 * permission denied
 * <li>{@link FailureMode#FILE_NOT_FOUND} — path does not exist or cannot be
 * resolved
 * <li>{@link FailureMode#IO_ERROR} — I/O operation failed
 * <li>{@link FailureMode#NETWORK_ERROR} — network operation failed
 * <li>{@link FailureMode#EXECUTION_TIMEOUT} — tool did not complete in time
 * <li>{@link FailureMode#SECURITY_VIOLATION} — security policy violation
 * </ul>
 *
 * <h2>Usage patterns</h2>
 * <ul>
 * <li>Single tool failure:
 * {@code throw new AiToolsFailureException(FailureMode.FILE_NOT_FOUND, "config.json", cause); }
 * <li>Argument validation:
 * {@code throw new AiToolsFailureException(FailureMode.ARGUMENT_VALIDATION, "maxResults must be 1-10, got: " + value); }
 * <li>Accumulate multiple failures:
 * {@code throw new AiToolsFailureException(existing, FailureMode.NETWORK_ERROR, "timeout connecting to search service", cause); }
 * </ul>
 *
 * <p>
 * The exception automatically generates a severity-appropriate message:
 * <ul>
 * <li>Single failures: "{mode}: {subject} - {detail}"
 * <li>Multiple failures: "Tool-calling encountered {count} error(s): {first};
 * {second}; ..."
 * </ul>
 */
public class AiToolsFailureException extends RuntimeException {

    /**
     * Categorizes the type of failure that occurred in the tool-calling
     * mechanism. Each mode represents a distinct failure scenario that callers
     * may want to handle differently.
     */
    public enum FailureMode {
        /**
         * A CDI bean, service dependency, or runtime resource required to execute the
         * tool is not available. Do NOT use for model-output failures (a model naming
         * an unknown tool is an {@code AiToolCallException}, not this).
         */
        TOOL_NOT_FOUND("Tool not registered"),

        /**
         * A service-layer business rule rejects a parameter value after the model's
         * output has been accepted as structurally valid (for example, maxResults=50
         * when the service only accepts 1–10). Do NOT use for schema-level or
         * type-level mismatches from the model — those are {@code AiToolCallException}.
         */
        ARGUMENT_VALIDATION("Argument validation failed"),

        /** File access denied due to security policy or permissions. */
        FILE_ACCESS_DENIED("File access denied"),

        /** File path requested does not exist or cannot be resolved. */
        FILE_NOT_FOUND("File not found"),

        /** I/O operation failed (read, write, directory listing, etc.). */
        IO_ERROR("I/O error"),

        /**
         * Network operation failed (timeout, connection refused, unreachable host, DNS
         * failure).
         */
        NETWORK_ERROR("Network error"),

        /**
         * Required resource is temporarily unavailable (disk full, memory exhausted,
         * service down).
         */
        RESOURCE_UNAVAILABLE("Resource unavailable"),

        /** Tool execution did not complete within configured time limit. */
        EXECUTION_TIMEOUT("Execution timeout"),

        /** Security policy violation or unauthorized operation. */
        SECURITY_VIOLATION("Security violation"),

        /**
         * Bug in tool implementation (null pointer exception, index out of bounds,
         * logic error).
         */
        TOOL_IMPLEMENTATION_ERROR("Tool implementation error"),

        /** Invalid configuration or missing required settings for tool execution. */
        CONFIGURATION_ERROR("Configuration error"),

        /** Error type cannot be determined from available context. */
        UNKNOWN("Unknown error");

        private final String label;

        FailureMode(final String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Records a single tool failure with mode, subject (what failed), and detail
     * (why).
     */
    static class ToolFailureRecord {
        final FailureMode mode;
        final String subject;
        final String detail;
        final Throwable cause;
        final long timestamp;

        ToolFailureRecord(final FailureMode mode, final String subject, final String detail,
                final Throwable cause) {
            this.mode = mode;
            this.subject = subject;
            this.detail = detail;
            this.cause = cause;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(mode.label());
            if (subject != null && !subject.isBlank()) {
                sb.append(" [").append(subject).append("]");
            }
            if (detail != null && !detail.isBlank()) {
                sb.append(": ").append(detail);
            }
            return sb.toString();
        }
    }

    private final List<ToolFailureRecord> failures;

    /**
     * Constructs an AiToolsFailureException with a single failure.
     *
     * @param mode    the type of failure
     * @param subject what failed (tool name, file path, parameter name, etc.)
     * @param detail  additional context (why it failed)
     */
    public AiToolsFailureException(final FailureMode mode, final String subject, final String detail) {
        super(formatMessage(List.of(new ToolFailureRecord(mode, subject, detail, null))));
        this.failures = List.of(new ToolFailureRecord(mode, subject, detail, null));
    }

    /**
     * Constructs an AiToolsFailureException with a single failure and causative
     * exception.
     *
     * @param mode    the type of failure
     * @param subject what failed (tool name, file path, parameter name, etc.)
     * @param detail  additional context (why it failed)
     * @param cause   the underlying exception
     */
    public AiToolsFailureException(final FailureMode mode, final String subject, final String detail,
            final Throwable cause) {
        super(formatMessage(List.of(new ToolFailureRecord(mode, subject, detail, cause))), cause);
        this.failures = List.of(new ToolFailureRecord(mode, subject, detail, cause));
    }

    /**
     * Constructs an AiToolsFailureException by accumulating an existing exception
     * with a new failure, preserving the failure history.
     *
     * <p>
     * Useful when multiple tool calls fail sequentially and you want to accumulate
     * all failures before propagating.
     *
     * @param existing the prior AiToolsFailureException to extend
     * @param mode     the new failure mode
     * @param subject  what failed in the new failure
     * @param detail   additional context for the new failure
     * @param cause    the underlying exception for the new failure
     */
    public AiToolsFailureException(final AiToolsFailureException existing, final FailureMode mode,
            final String subject, final String detail, final Throwable cause) {
        super(formatMessage(combine(existing.failures, new ToolFailureRecord(mode, subject, detail, cause))),
                cause != null ? cause : existing.getCause());
        this.failures = combine(existing.failures, new ToolFailureRecord(mode, subject, detail, cause));
    }

    /**
     * Returns an immutable list of all accumulated tool failures.
     *
     * @return list of ToolFailureRecord; each represents one failure
     */
    public List<ToolFailureRecord> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    /**
     * Returns the count of accumulated failures.
     *
     * @return number of failures recorded
     */
    public int getFailureCount() {
        return failures.size();
    }

    /**
     * Returns the first failure's mode.
     *
     * @return FailureMode of the first failure, or null if no failures
     */
    public FailureMode getFirstFailureMode() {
        return failures.isEmpty() ? null : failures.get(0).mode;
    }

    /**
     * Checks if any failure has the specified mode.
     *
     * @param mode the failure mode to search for
     * @return true if at least one failure matches the mode
     */
    public boolean hasFailureMode(final FailureMode mode) {
        return failures.stream().anyMatch(f -> f.mode == mode);
    }

    /**
     * Returns the subject (what failed) of the first failure.
     *
     * @return subject string, or null if no failures or subject not set
     */
    public String getFirstSubject() {
        return failures.isEmpty() ? null : failures.get(0).subject;
    }

    /**
     * Returns all subjects from all failures.
     *
     * @return list of subjects; empty if no failures
     */
    public List<String> getAllSubjects() {
        return failures.stream()
                .map(f -> f.subject)
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(super.toString());
        if (failures.size() > 1) {
            sb.append("\nAccumulated tool failures (").append(failures.size()).append("):\n");
            for (int i = 0; i < failures.size(); i++) {
                sb.append("  [").append(i + 1).append("] ").append(failures.get(i)).append("\n");
            }
        }
        return sb.toString();
    }

    private static String formatMessage(final List<ToolFailureRecord> records) {
        if (records == null || records.isEmpty()) {
            throw new AssertionError("CRITICAL: AiToolsFailureException constructed with no failures. "
                    + "This indicates a programming error. "
                    + "Application must terminate immediately.");
        }

        if (records.size() == 1) {
            return records.get(0).toString();
        }

        final StringBuilder sb = new StringBuilder("Tool-calling encountered ")
                .append(records.size())
                .append(" error(s): ");
        for (int i = 0; i < Math.min(records.size(), 3); i++) {
            if (i > 0)
                sb.append("; ");
            sb.append(records.get(i));
        }
        if (records.size() > 3) {
            sb.append("; and ").append(records.size() - 3).append(" more");
        }
        return sb.toString();
    }

    private static List<ToolFailureRecord> combine(final List<ToolFailureRecord> existing,
            final ToolFailureRecord newFailure) {
        final List<ToolFailureRecord> combined = new ArrayList<>(existing);
        combined.add(newFailure);
        return combined;
    }
}
