package ac.uk.sussex.kn253.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Represents a fully parsed and validated tool-call request produced by
 * {@code ToolDispatcher.parseToolCall(String)}.
 *
 * <p>
 * A {@code ToolCall} instance is only constructed after the model's raw output
 * has been confirmed to be well-formed JSON naming a registered tool. It is
 * therefore safe to pass directly to {@code AnalysisService} without further
 * validation.
 *
 * <p>
 * Constraints: no reflections, no switch statements, no ternary expressions,
 * no boxed types.
 *
 * @param name      the name of the tool to invoke; matches a registered
 *                  {@link ToolDefinition#name()}
 * @param arguments the arguments extracted from the model's JSON output;
 *                  never {@code null}, but may be an empty {@link ObjectNode}
 */
public record ToolCall(String name, ObjectNode arguments) {
}
