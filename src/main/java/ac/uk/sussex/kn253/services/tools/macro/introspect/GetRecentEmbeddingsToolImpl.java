package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.model.EmbeddingMatch;
import ac.uk.sussex.kn253.services.EmbeddingService;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacro;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacroDefinition;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

/**
 * Tool for retrieving recently generated embeddings.
 */
public class GetRecentEmbeddingsToolImpl implements ToolMacro {

    private static final Logger LOG = Logger.getLogger(GetRecentEmbeddingsToolImpl.class);
    private static final String SESSION_ID = "default-chat-session";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ToolSpecification specification;
    private final EmbeddingService embeddingService;

    public GetRecentEmbeddingsToolImpl(final EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
        this.specification = ToolSpecification.builder()
                .name("get_recent_embeddings")
                .description("Retrieve recently generated embeddings from the current conversation session.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("limit",
                                JsonIntegerSchema.builder()
                                        .description("Maximum number of results (default 10, max 50).")
                                        .build())
                        .build())
                .build();
    }

    @Override
    public ToolMacroDefinition definition() {
        // Embedding tools are optional and dynamically loaded
        return new ToolMacroDefinition("get_recent_embeddings", List.of());
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

        int limit = 10;
        final Object limitObj = args.get("limit");
        if (limitObj instanceof Number) {
            limit = ((Number) limitObj).intValue();
        }
        if (limit < 1) {
            limit = 10;
        }
        if (limit > 50) {
            limit = 50;
        }

        try {
            final List<EmbeddingMatch> recentEmbeddings = embeddingService.getRecentEmbeddings(SESSION_ID, limit);

            if (recentEmbeddings.isEmpty()) {
                return "No recent embeddings found. Embeddings are generated when user input is processed.";
            }

            final StringBuilder sb = new StringBuilder();
            sb.append("Recent embeddings (").append(recentEmbeddings.size()).append(" items):\n\n");

            for (int i = 0; i < recentEmbeddings.size(); i++) {
                final EmbeddingMatch embedding = recentEmbeddings.get(i);
                final String timestamp = formatTimestamp(embedding.timestamp());

                sb.append(i + 1).append(". ").append(embedding.sourceType());
                if (embedding.sourceId() != null && !embedding.sourceId().isBlank()) {
                    sb.append(" (").append(embedding.sourceId()).append(")");
                }
                sb.append(" - ").append(timestamp).append("\n");
                if (embedding.text() != null) {
                    sb.append("   ").append(truncateText(embedding.text(), 100)).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString().trim();
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to retrieve recent embeddings: %s", e.getMessage());
            return "Error retrieving recent embeddings: " + e.getMessage();
        }
    }

    private String formatTimestamp(final long timestampMs) {
        try {
            return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(timestampMs));
        } catch (final Exception e) {
            return "unknown";
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
