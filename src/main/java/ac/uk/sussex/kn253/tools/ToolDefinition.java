package ac.uk.sussex.kn253.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ac.uk.sussex.kn253.util.JsonMarkdownRenderer;

/**
 * Immutable description of a tool that the model may request.
 *
 * <p>
 * {@code ToolDefinition} is the canonical schema artefact for the explicit
 * dispatch path. Instances are built once at startup in
 * {@link ToolRegistry} and are never mutated after construction.
 *
 * <p>
 * The {@code schema} field is a Jackson {@link ObjectNode} (from
 * {@code quarkus-rest-jackson} / {@code com.fasterxml.jackson.databind})
 * whose structure matches the JSON object the model is instructed to produce
 * when it wants to invoke this tool (for example
 * {@code {"exec": "bash -c ls"}}).
 * Call {@link #schemaAsString()} to serialise it as compact JSON, or
 * {@link #schemaAsMarkdown()} to render it as human-readable Markdown.
 *
 * <p>
 * Use the static factory methods to build commonly-repeated schema fragments:
 * <ul>
 * <li>{@link #emptySchema()} — schema with no required keys
 * <li>{@link #execSchema(String)} — schema with a single {@code exec} key
 * <li>{@link #pathSchema(String)} — schema with a single {@code path} key
 * </ul>
 *
 * <p>
 * Constraints: no reflections, no switch statements, no ternary expressions,
 * no boxed types.
 *
 * @param name        unique tool name; must match the name the model will use
 * @param description human-readable description passed to the model
 * @param schema      JSON schema as a Jackson {@link ObjectNode};
 *                    never {@code null}
 */
public record ToolDefinition(String name, String description, ObjectNode schema) {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final JsonMarkdownRenderer MARKDOWN_RENDERER = new JsonMarkdownRenderer(MAPPER);

    private static final String KEY_EXEC = "exec";
    private static final String KEY_PATH = "path";

    /**
     * Returns the schema serialised as a compact JSON string, suitable for
     * embedding in a prompt or system message sent to the model.
     *
     * @return the JSON string representation of the schema; never {@code null}
     */
    public String schemaAsString() {
        return schema.toString();
    }

    /**
     * Returns the schema rendered as a Markdown document using
     * {@link JsonMarkdownRenderer}. Field names are converted to Title Case
     * headings; nested objects are recursed; arrays render as bullet lists.
     *
     * <p>
     * Use this when embedding a tool's schema in human-readable prompt context
     * rather than a raw JSON instruction.
     *
     * @return the Markdown representation of the schema; never {@code null}
     */
    public String schemaAsMarkdown() {
        return MARKDOWN_RENDERER.render(schema, name);
    }

    /**
     * Returns a schema {@link ObjectNode} with no keys — for tools that require
     * no arguments from the model.
     *
     * @return a new, empty {@link ObjectNode}
     */
    public static ObjectNode emptySchema() {
        return JsonNodeFactory.instance.objectNode();
    }

    /**
     * Returns a schema {@link ObjectNode} with a single {@code exec} key set to
     * the provided value. Suitable for tools that accept a shell command string.
     *
     * @param execValue the value to assign to the {@code exec} key
     * @return a new {@link ObjectNode} containing {@code {"exec": execValue}}
     */
    public static ObjectNode execSchema(final String execValue) {
        final ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(KEY_EXEC, execValue);
        return node;
    }

    /**
     * Returns a schema {@link ObjectNode} with a single {@code path} key set to
     * the provided value. Suitable for tools that accept a filesystem path.
     *
     * @param pathValue the value to assign to the {@code path} key
     * @return a new {@link ObjectNode} containing {@code {"path": pathValue}}
     */
    public static ObjectNode pathSchema(final String pathValue) {
        final ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(KEY_PATH, pathValue);
        return node;
    }
}
