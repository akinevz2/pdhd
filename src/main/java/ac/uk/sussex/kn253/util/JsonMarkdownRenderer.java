package ac.uk.sussex.kn253.util;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converts any POJO or Jackson {@link JsonNode} to a Markdown document.
 *
 * <ul>
 * <li>Root object fields become heading sections (starting at the configured
 * base depth).
 * <li>Plain string / number / boolean values render as paragraph text.
 * <li>Arrays of strings render as unordered bullet lists.
 * <li>Arrays of objects render each item as a single bullet with inline
 * <code>**key**: value</code> pairs separated by {@code ·}.
 * <li>Nested objects are recursed with one additional heading level.
 * <li>camelCase field names are split into "Title Case" headings automatically.
 * </ul>
 */
public final class JsonMarkdownRenderer {

    /** Maximum Markdown heading level (ATX headings go up to ######). */
    private static final int MAX_HEADING_DEPTH = 6;

    private final ObjectMapper mapper;

    public JsonMarkdownRenderer(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Render {@code pojo} as a Markdown document with an optional H2 title.
     *
     * @param pojo  any Jackson-serialisable object
     * @param title top-level heading text, or {@code null} to omit
     * @return rendered Markdown string
     */
    public String render(final Object pojo, final String title) {
        final JsonNode root = mapper.valueToTree(pojo);
        final StringBuilder sb = new StringBuilder(2048);
        if (title != null && !title.isBlank()) {
            sb.append("## ").append(title.trim()).append("\n\n");
        }
        renderNode(root, sb, 3);
        return sb.toString().trim();
    }

    // ──────────────────────────────────────────────────────────────── private ──

    private void renderNode(final JsonNode node, final StringBuilder sb, final int depth) {
        if (node.isObject()) {
            renderObject(node, sb, depth);
        } else if (node.isArray()) {
            renderArray(node, sb);
        } else {
            renderScalar(node, sb);
        }
    }

    private void renderObject(final JsonNode node, final StringBuilder sb, final int depth) {
        node.fieldNames().forEachRemaining(fieldName -> {
            sb.append(heading(depth)).append(formatKey(fieldName)).append("\n\n");
            renderNode(node.get(fieldName), sb, depth + 1);
            sb.append("\n");
        });
    }

    private void renderArray(final JsonNode array, final StringBuilder sb) {
        if (array.isEmpty()) {
            sb.append("_None._\n");
            return;
        }

        for (final JsonNode item : array) {
            if (item.isNull() || item.isMissingNode()) {
                continue;
            }
            if (item.isObject()) {
                sb.append("- ").append(inlineObject(item)).append("\n");
            } else {
                final String text = item.asText().trim();
                if (!text.isBlank()) {
                    sb.append("- ").append(text).append("\n");
                }
            }
        }
    }

    private void renderScalar(final JsonNode node, final StringBuilder sb) {
        if (node.isNull() || node.isMissingNode()) {
            sb.append("_None._\n");
        } else {
            final String text = node.asText().trim();
            sb.append(text.isEmpty() ? "_None._" : text).append("\n");
        }
    }

    /**
     * Renders a JSON object as an inline bullet string:
     * {@code **Key One**: value · **Key Two**: value}.
     */
    private static String inlineObject(final JsonNode node) {
        final List<String> parts = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldName -> {
            final JsonNode v = node.get(fieldName);
            if (!v.isNull() && !v.isMissingNode()) {
                final String text = v.asText().trim();
                if (!text.isBlank()) {
                    parts.add("**" + formatKey(fieldName) + "**: " + text);
                }
            }
        });
        return String.join(" · ", parts);
    }

    /** Returns an ATX heading prefix of depth {@code d}, clamped to 1–6. */
    private static String heading(final int d) {
        return "#".repeat(Math.clamp(d, 1, MAX_HEADING_DEPTH)) + " ";
    }

    /**
     * Converts a camelCase or snake_case identifier to "Title Case".
     * <p>
     * Examples: {@code folderPath} → {@code Folder Path},
     * {@code probableTechnologies} → {@code Probable Technologies}.
     */
    static String formatKey(final String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        final String spaced = key
                .replaceAll("([A-Z])", " $1")
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
