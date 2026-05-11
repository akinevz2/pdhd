package ac.uk.sussex.kn253.tools;

import java.util.*;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ac.uk.sussex.kn253.AiToolCallException;
import ac.uk.sussex.kn253.ConversationalException;
import ac.uk.sussex.kn253.services.AnalysisService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Routes model tool-call requests to {@link AnalysisService}, maintaining a
 * typed per-request failure accumulator.
 *
 * <h2>Design constraints</h2>
 * <ul>
 * <li>Completely functional — no method has side effects beyond the
 * accumulator passed in as a parameter.
 * <li>All human-readable strings are declared as {@code static final String}
 * constants at the top of the file.
 * <li>All throwing methods contain an explicit {@code try-catch}; no untyped
 * exception propagates.
 * <li>No switch statements, no ternary expressions.
 * </ul>
 *
 * <h2>Error accumulation</h2>
 * <p>
 * Callers create a fresh {@code List<AiToolCallException>} per user message,
 * pass it to every method, and inspect it after the call. When the accumulator
 * is non-empty the caller must throw a {@link ConversationalException} wrapping
 * all accrued failures and must <em>not</em> invoke the Ollama REST API again.
 *
 * <h2>Schema validation</h2>
 * <p>
 * After parsing, the dispatcher compares the keys present in the model's
 * {@code arguments} object against the required keys declared in the matching
 * {@link ToolDefinition} schema. On any mismatch a corrective message string
 * is produced for the caller to forward to the model. Up to
 * {@link #MAX_SCHEMA_RETRIES} correction attempts are allowed; on exhaustion
 * an {@link AiToolCallException} is added to the accumulator.
 */
@ApplicationScoped
public class ToolDispatcher {

    private static final Logger LOG = Logger.getLogger(ToolDispatcher.class);

    // ── human-readable constants ───────────────────────────────────────────────

    static final String MSG_MALFORMED = "Malformed tool-call output from model";
    static final String MSG_UNKNOWN_TOOL = "Model called unknown tool: ";
    static final String MSG_RETRIES_EXHAUSTED = "Schema correction retries exhausted for tool: ";
    static final String MSG_ACCUMULATOR_NULL = "Error accumulator must not be null";
    static final String MSG_ACCUMULATOR_NULL_ITEM = "Error accumulator must not contain null items";
    static final String CORRECTIVE_PREFIX = "Your previous tool-call was rejected. "
            + "Resubmit as a single JSON object matching exactly this schema — "
            + "no extra keys, no plain text before or after it:\n";

    static final int MAX_SCHEMA_RETRIES = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    AnalysisService analysisService;

    @Inject
    ToolRegistry toolRegistry;

    // ── public API ─────────────────────────────────────────────────────────────

    /**
     * Parses {@code rawModelOutput} into a {@link ToolCall}, validates the
     * tool name against the registry, and returns the result.
     *
     * <p>
     * On any parse or validation failure the exception is added to
     * {@code accumulator} and {@code null} is returned — the caller must check
     * the accumulator before proceeding.
     *
     * @param rawModelOutput the raw string returned by the model
     * @param accumulator    per-request failure list; must not be {@code null}
     *                       and must not contain {@code null} items
     * @return parsed {@link ToolCall}, or {@code null} if an error was accrued
     */
    public ToolCall parseToolCall(final String rawModelOutput,
            final List<AiToolCallException> accumulator) {
        validateAccumulator(accumulator);
        try {
            final JsonNode parsed = MAPPER.readTree(rawModelOutput);
            if (!(parsed instanceof ObjectNode)) {
                throw new IllegalArgumentException(MSG_MALFORMED);
            }

            final ObjectNode json = (ObjectNode) parsed;
            final String name = json.path("name").asText();
            final JsonNode argumentsNode = json.get("arguments");
            final ObjectNode args;

            if (argumentsNode == null) {
                args = MAPPER.createObjectNode();
            } else if (argumentsNode instanceof ObjectNode) {
                args = (ObjectNode) argumentsNode;
            } else {
                throw new IllegalArgumentException(MSG_MALFORMED);
            }

            if (!toolRegistry.isKnownTool(name)) {
                final AiToolCallException ex = new AiToolCallException(MSG_UNKNOWN_TOOL + name);
                accumulator.add(ex);
                return null;
            }

            return new ToolCall(name, args);
        } catch (final Exception e) {
            final AiToolCallException ex = new AiToolCallException(MSG_MALFORMED, e, rawModelOutput);
            accumulator.add(ex);
            return null;
        }
    }

    /**
     * Validates the schema of a parsed {@link ToolCall} against the expected
     * keys in the matching {@link ToolDefinition}.
     *
     * <p>
     * Returns {@code null} (no corrective action needed) when all required keys
     * are present. Returns a corrective message string when keys are missing or
     * extra keys are present — the caller must forward this string to the model
     * as a System or Tool-tagged message and retry.
     *
     * <p>
     * On exhaustion of {@code retryCount} the failure is added to
     * {@code accumulator} and {@code null} is returned with the accumulator
     * non-empty.
     *
     * @param toolCall    the parsed tool call to validate
     * @param retryCount  number of correction attempts already made for this
     *                    tool call (0 on first attempt)
     * @param accumulator per-request failure list; must not be {@code null}
     * @return a corrective message string if the schema does not match, or
     *         {@code null} if the call is valid (or retries exhausted)
     */
    public String validateSchema(final ToolCall toolCall, final int retryCount,
            final List<AiToolCallException> accumulator) {
        validateAccumulator(accumulator);
        try {
            if (retryCount >= MAX_SCHEMA_RETRIES) {
                final AiToolCallException ex = new AiToolCallException(
                        MSG_RETRIES_EXHAUSTED + toolCall.name());
                accumulator.add(ex);
                return null;
            }

            final ToolDefinition definition = toolRegistry.findByName(toolCall.name());
            if (definition == null) {
                final AiToolCallException ex = new AiToolCallException(MSG_UNKNOWN_TOOL + toolCall.name());
                accumulator.add(ex);
                return null;
            }

            final List<String> mismatches = collectSchemaMismatches(
                    definition.schema(), toolCall.arguments());
            if (mismatches.isEmpty()) {
                return null;
            }

            LOG.debugf("Schema mismatch for tool '%s' (retry %d): %s",
                    toolCall.name(), retryCount, mismatches);
            return CORRECTIVE_PREFIX + definition.schemaAsMarkdown();
        } catch (final Exception e) {
            final AiToolCallException ex = new AiToolCallException(
                    MSG_RETRIES_EXHAUSTED + toolCall.name(), e, null);
            accumulator.add(ex);
            return null;
        }
    }

    /**
     * Dispatches a validated {@link ToolCall} to {@link AnalysisService} and
     * returns the result string.
     *
     * <p>
     * Any exception thrown by {@code AnalysisService} is wrapped in an
     * {@link AiToolCallException}, added to the accumulator, and {@code null}
     * is returned.
     *
     * @param toolCall    the validated tool call to execute
     * @param accumulator per-request failure list; must not be {@code null}
     * @return the result string, or {@code null} if an error was accrued
     */
    public String dispatch(final ToolCall toolCall,
            final List<AiToolCallException> accumulator) {
        validateAccumulator(accumulator);
        try {
            return analysisService.execute(toolCall);
        } catch (final AiToolCallException e) {
            accumulator.add(e);
            return null;
        } catch (final Exception e) {
            final AiToolCallException ex = new AiToolCallException(MSG_UNKNOWN_TOOL + toolCall.name(), e, null);
            accumulator.add(ex);
            return null;
        }
    }

    /**
     * Converts a non-empty accumulator into a {@link ConversationalException}
     * wrapping all accrued failures.
     *
     * <p>
     * The caller must invoke this method and propagate the result whenever the
     * accumulator is non-empty — no further Ollama REST API calls must be made
     * for that request.
     *
     * @param accumulator non-empty per-request failure list
     * @return a {@link ConversationalException} containing all accrued failures
     * @throws IllegalArgumentException if the accumulator is empty
     */
    public ConversationalException buildConversationalException(
            final List<AiToolCallException> accumulator) {
        if (accumulator.isEmpty()) {
            throw new IllegalArgumentException("Accumulator is empty — nothing to propagate");
        }
        return new ConversationalException(accumulator.toArray(new Throwable[0]));
    }

    /**
     * Creates a fresh, empty per-request error accumulator.
     *
     * <p>
     * Callers must create one accumulator per user message and pass it to all
     * {@code ToolDispatcher} methods for that message.
     *
     * @return a new, empty, mutable {@code List<AiToolCallException>}
     */
    public List<AiToolCallException> newAccumulator() {
        return new ArrayList<>();
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * Returns a list of human-readable mismatch descriptions between the keys
     * the schema declares and the keys the model supplied.
     *
     * <p>
     * Missing keys (declared in schema but absent from model args) and extra
     * keys (present in model args but absent from schema) are both reported.
     * Returns an empty list when the key sets are identical.
     */
    private static List<String> collectSchemaMismatches(final ObjectNode schema,
            final ObjectNode modelArgs) {
        final List<String> mismatches = new ArrayList<>();

        final Iterator<String> schemaKeys = schema.fieldNames();
        while (schemaKeys.hasNext()) {
            final String key = schemaKeys.next();
            if (!modelArgs.has(key)) {
                mismatches.add("missing required key: " + key);
            }
        }

        final Iterator<String> argKeys = modelArgs.fieldNames();
        while (argKeys.hasNext()) {
            final String key = argKeys.next();
            if (!schema.has(key)) {
                mismatches.add("unexpected extra key: " + key);
            }
        }

        return mismatches;
    }

    /**
     * Guards against a null accumulator or a null item already in it.
     * Fails fast before any dispatch work is attempted.
     */
    private static void validateAccumulator(final List<AiToolCallException> accumulator) {
        if (accumulator == null) {
            throw new IllegalArgumentException(MSG_ACCUMULATOR_NULL);
        }
        for (final AiToolCallException item : accumulator) {
            if (item == null) {
                throw new IllegalArgumentException(MSG_ACCUMULATOR_NULL_ITEM);
            }
        }
    }
}
