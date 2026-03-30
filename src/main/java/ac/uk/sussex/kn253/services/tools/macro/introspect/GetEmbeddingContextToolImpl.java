package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.model.EmbeddingMatch;
import ac.uk.sussex.kn253.services.EmbeddingService;
import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacro;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacroDefinition;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;

/**
 * Tool for searching embeddings by semantic similarity.
 */
public class GetEmbeddingContextToolImpl implements ToolMacro {

    private static final Logger LOG = Logger.getLogger(GetEmbeddingContextToolImpl.class);
    private static final String SESSION_ID = "default-chat-session";

    private final ToolSpecification specification;
    private final EmbeddingService embeddingService;

    public GetEmbeddingContextToolImpl(final EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
        this.specification = ToolSpecification.builder()
                .name("get_embedding_context")
                .description(definition().description())
                .parameters(JsonObjectSchema.builder()
                        .addProperty("query",
                                JsonStringSchema.builder()
                                        .description("The search query to find similar content.")
                                        .build())
                        .addProperty("limit",
                                JsonIntegerSchema.builder()
                                        .description("Maximum number of results (default 5, max 20).")
                                        .build())
                        .required("query")
                        .build())
                .build();
    }

    @Override
    public ToolMacroDefinition definition() {
        // Embedding tools are optional and dynamically loaded
        return new ToolMacroDefinition("get_embedding_context",
                "Search for semantically similar content from recent embeddings in the current conversation session.",
                Map.of(),
                List.of("context", "keywords", "embeddings"));
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        if (embeddingService == null || !embeddingService.isEnabled()) {
            return "Embeddings are not enabled in this session.";
        }

        final String query = ToolArguments.getString(args, "query", "").trim();
        if (query.isBlank()) {
            return "Error: 'query' argument is required and cannot be empty";
        }

        int limit = 5;
        final Object limitObj = args.get("limit");
        if (limitObj instanceof Number) {
            limit = ((Number) limitObj).intValue();
        }
        if (limit < 1) {
            limit = 5;
        }
        if (limit > 20) {
            limit = 20;
        }

        try {
            final List<EmbeddingMatch> results = embeddingService.search(query, limit, SESSION_ID);

            if (results.isEmpty()) {
                return "No semantically similar content found for query: " + query;
            }

            final StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size())
                    .append(" semantically similar items for query: '").append(query).append("'\n\n");

            for (int i = 0; i < results.size(); i++) {
                final EmbeddingMatch match = results.get(i);
                sb.append(i + 1).append(". [").append(String.format("%.1f%%", match.similarity() * 100))
                        .append(" match] ");
                sb.append(match.sourceType()).append(": ");
                if (match.sourceId() != null && !match.sourceId().isBlank()) {
                    sb.append(match.sourceId()).append("\n");
                } else {
                    sb.append("\n");
                }
                if (match.text() != null) {
                    sb.append("   ").append(truncateText(match.text(), 150)).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString().trim();
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to search embeddings: %s", e.getMessage());
            return "Error searching embeddings: " + e.getMessage();
        }
    }

    private String truncateText(final String text, final int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }
}
