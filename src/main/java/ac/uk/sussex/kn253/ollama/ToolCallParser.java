package ac.uk.sussex.kn253.ollama;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.services.ToolService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * Parses AI tool-call requests that arrive as raw text rather than through the
 * structured tool-call API.
 *
 * <p>Some Ollama models (notably qwen2.5-coder variants) do not support the
 * native tool-calling response format and instead emit tool invocations as JSON
 * embedded inside the assistant message text.  This parser handles several
 * output shapes:
 *
 * <ol>
 *   <li>XML-tagged blocks: {@code <tool_call>{ … }</tool_call>}</li>
 *   <li>JSON code fences: {@code ```json { … } ```}</li>
 *   <li>Partially-closed {@code <tool_call>} tags (model truncation bug)</li>
 *   <li>Bare JSON object or array at the top level of the message</li>
 * </ol>
 *
 * <p>This class is a stateless utility; all methods are static.
 */
public final class ToolCallParser {

    private static final Logger LOG = Logger.getLogger(ToolCallParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Strips control tokens such as {@code <|eot_id|>} from model output. */
    private static final Pattern CONTROL_TOKEN_PATTERN = Pattern.compile("<\\|[^|>]+\\|>");

    /**
     * Matches tool-call payloads inside {@code <tool_call>…</tool_call>} XML
     * blocks or {@code ```json … ```} fenced code blocks.
     */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "<tool_call>\\s*(\\{[\\s\\S]*?\\})\\s*</tool_call>"
                    + "|```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```",
            Pattern.CASE_INSENSITIVE);

    private ToolCallParser() {
        // utility class
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parses zero or more tool-call requests from raw model output text.
     *
     * <p>Only calls whose names appear in {@code toolService}'s registered
     * specifications are returned; unrecognised names are silently dropped.
     *
     * @param text        the assistant message text to parse; may be {@code null}.
     * @param toolService the service used to validate tool names.
     * @return an immutable list of tool-execution requests; never {@code null}.
     */
    public static List<ToolExecutionRequest> parse(final String text, final ToolService toolService) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        final String sanitized = sanitize(text);
        final List<ToolExecutionRequest> results = new ArrayList<>();

        // 1. Try tagged / fenced blocks.
        final Matcher m = TOOL_CALL_PATTERN.matcher(sanitized);
        while (m.find()) {
            final String json = m.group(1) != null ? m.group(1) : m.group(2);
            results.addAll(parseJson(json, toolService));
        }
        if (!results.isEmpty()) {
            return List.copyOf(results);
        }

        // 2. Handle malformed outputs with an unclosed <tool_call> tag.
        final Optional<String> embedded = extractJsonAfterToolCallTag(sanitized);
        if (embedded.isPresent()) {
            results.addAll(parseJson(embedded.get(), toolService));
            if (!results.isEmpty()) {
                return List.copyOf(results);
            }
        }

        // 3. Treat the whole message as a bare JSON object or array.
        final String trimmed = sanitized.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            results.addAll(parseJson(trimmed, toolService));
        }
        return List.copyOf(results);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Removes control tokens (e.g. {@code <|eot_id|>}) from {@code raw}. */
    private static String sanitize(final String raw) {
        return CONTROL_TOKEN_PATTERN.matcher(raw).replaceAll("").trim();
    }

    /**
     * Extracts the first well-formed JSON object that follows an opening
     * {@code <tool_call>} tag.  Returns {@link Optional#empty()} when either
     * the tag or a matching pair of braces cannot be found.
     */
    private static Optional<String> extractJsonAfterToolCallTag(final String text) {
        final int tagIdx = text.toLowerCase(java.util.Locale.ROOT).indexOf("<tool_call>");
        if (tagIdx < 0) {
            return Optional.empty();
        }
        final int jsonStart = text.indexOf('{', tagIdx);
        if (jsonStart < 0) {
            return Optional.empty();
        }
        int depth = 0;
        for (int i = jsonStart; i < text.length(); i++) {
            final char ch = text.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(text.substring(jsonStart, i + 1));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Parses a JSON string into one or more {@link ToolExecutionRequest}s.
     * Handles single objects, arrays of objects, and OpenAI-style
     * {@code {"tool_calls":[…]}} wrappers.
     *
     * @param json        raw JSON string.
     * @param toolService used to validate discovered tool names.
     * @return a modifiable list (may be empty); never {@code null}.
     */
    private static List<ToolExecutionRequest> parseJson(final String json, final ToolService toolService) {
        try {
            final JsonNode root = MAPPER.readTree(json);
            final List<ToolExecutionRequest> calls = new ArrayList<>();
            if (root == null || root.isNull()) {
                return calls;
            }

            if (root.isArray()) {
                for (final JsonNode node : root) {
                    parseOne(node, toolService).ifPresent(calls::add);
                }
                return calls;
            }

            // OpenAI-style wrapper: { "tool_calls": [ … ] }
            if (root.has("tool_calls") && root.get("tool_calls").isArray()) {
                for (final JsonNode node : root.get("tool_calls")) {
                    parseOne(node, toolService).ifPresent(calls::add);
                }
                if (!calls.isEmpty()) {
                    return calls;
                }
            }

            parseOne(root, toolService).ifPresent(calls::add);
            return calls;
        } catch (final Exception e) {
            LOG.debugf("Could not parse text as tool call JSON payload: %s", e.getMessage());
            return List.of();
        }
    }

    /**
     * Parses a single JSON node into a {@link ToolExecutionRequest}.
     * Recognises both {@code {"name":…,"arguments":…}} and OpenAI-style
     * {@code {"function":{"name":…,"arguments":…}}} shapes.
     *
     * <p>Returns {@link Optional#empty()} when the name is absent or does not
     * correspond to a registered tool.
     *
     * @param node        parsed JSON node representing one tool call.
     * @param toolService used to validate the resolved tool name.
     */
    private static Optional<ToolExecutionRequest> parseOne(
            final JsonNode node,
            final ToolService toolService) {
        try {
            String name = null;
            String arguments = "{}";

            if (node.has("name")) {
                name = node.get("name").asText();
                final JsonNode argsNode = node.has("arguments") ? node.get("arguments")
                        : node.has("parameters") ? node.get("parameters") : null;
                if (argsNode != null) {
                    arguments = argsNode.isObject() ? MAPPER.writeValueAsString(argsNode) : argsNode.asText();
                }
            } else if (node.has("function")) {
                final JsonNode fn = node.get("function");
                name = fn.has("name") ? fn.get("name").asText() : null;
                final JsonNode argsNode = fn.has("arguments") ? fn.get("arguments") : null;
                if (argsNode != null) {
                    arguments = argsNode.isObject() ? MAPPER.writeValueAsString(argsNode) : argsNode.asText();
                }
            }

            if (name == null) {
                return Optional.empty();
            }
            final String resolvedName = name;
            if (toolService.toolSpecifications().stream().noneMatch(s -> s.name().equals(resolvedName))) {
                LOG.debugf("Text contained JSON but no matching tool found for name: %s", resolvedName);
                return Optional.empty();
            }
            return Optional.of(ToolExecutionRequest.builder().name(resolvedName).arguments(arguments).build());
        } catch (final Exception e) {
            LOG.debugf("Could not parse text as tool call: %s", e.getMessage());
            return Optional.empty();
        }
    }
}
