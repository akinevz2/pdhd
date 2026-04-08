package ac.uk.sussex.kn253.services;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import ac.uk.sussex.kn253.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Semantic retrieval service over persisted chunk embeddings.
 */
@ApplicationScoped
public class EmbeddingRetrievalService {

    /**
     * RAFT-split context containing oracle documents (high-similarity, likely
     * relevant) and distractor documents (lower-similarity, potentially
     * irrelevant). Both sections are pre-formatted with RAFT labels and combined
     * into a single {@link #combined()} string ready for prompt injection.
     */
    public record RaftContext(String oracleSection, String distractorSection, String combined) {

        /** True when both oracle and distractor sections are empty. */
        public boolean isEmpty() {
            return (oracleSection == null || oracleSection.isBlank())
                    && (distractorSection == null || distractorSection.isBlank());
        }
    }

    private static final Logger LOG = Logger.getLogger(EmbeddingRetrievalService.class.getName());

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    OllamaConfig ollamaConfig;

    @Inject
    OllamaRuntimeEndpointService runtimeEndpointService;

    @Inject
    ObjectMapper objectMapper;

    public String retrieveContext(final ProjectFolder project, final String query, final int limit) {
        return retrieveContext(project, query, limit, sourceKey -> true);
    }

    public String retrieveContext(final ProjectFolder project, final String query, final int limit,
            final Predicate<String> sourceKeyFilter) {
        if (project == null || query == null || query.isBlank()
                || !Boolean.TRUE.equals(ollamaConfig.embeddingEnabled())) {
            return "";
        }

        final String modelName = resolveEmbeddingModelName();
        if (modelName == null || modelName.isBlank()) {
            return "";
        }

        final List<Double> queryVector = embed(query, modelName);
        if (queryVector.isEmpty()) {
            return "";
        }

        final List<EmbeddingChunk> chunks = EmbeddingChunk.find("project = ?1", project).list();
        if (chunks.isEmpty()) {
            return "";
        }

        final Predicate<String> effectiveSourceKeyFilter = sourceKeyFilter == null ? sourceKey -> true
                : sourceKeyFilter;
        final List<ScoredChunk> scored = new ArrayList<>();
        for (final EmbeddingChunk chunk : chunks) {
            if (!effectiveSourceKeyFilter.test(chunk.sourceKey)) {
                continue;
            }
            final List<Double> vector = readVector(chunk.embeddingJson);
            if (vector.isEmpty()) {
                continue;
            }
            final double score = cosineSimilarity(queryVector, vector);
            if (!Double.isFinite(score)) {
                continue;
            }
            scored.add(new ScoredChunk(chunk, score));
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        final int top = Math.max(1, limit);
        final StringBuilder sb = new StringBuilder(4096);
        sb.append("Retrieved context:\n");
        for (int i = 0; i < scored.size() && i < top; i++) {
            final ScoredChunk item = scored.get(i);
            sb.append("- source=").append(item.chunk.sourceKey)
                    .append(", chunk=").append(item.chunk.chunkIndex)
                    .append(", score=").append(String.format(Locale.ROOT, "%.4f", item.score))
                    .append("\n")
                    .append(item.chunk.chunkText)
                    .append("\n\n");
        }
        return sb.toString().trim();
    }

    private String resolveEmbeddingModelName() {
        final LLMSettings settings = modelConfigService.load();
        final String configured = settings == null ? null : settings.getEmbeddingModelName();
        if (configured == null) {
            return ollamaConfig.embeddingModelName();
        }
        final String trimmed = configured.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private List<Double> embed(final String text, final String modelName) {
        final String baseUrl = runtimeEndpointService.getActiveBaseUrl();
        final String endpoint = baseUrl.endsWith("/") ? baseUrl + "api/embeddings" : baseUrl + "/api/embeddings";

        final Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("input", text);

        try {
            final String payload = objectMapper.writeValueAsString(requestBody);
            final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            final HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 300) {
                return List.of();
            }

            final Map<String, Object> parsed = objectMapper.readValue(
                    response.body(),
                    new TypeReference<Map<String, Object>>() {
                    });
            final Object embedding = parsed.get("embedding");
            if (!(embedding instanceof final List<?> list)) {
                return List.of();
            }
            final List<Double> vector = new ArrayList<>(list.size());
            for (final Object value : list) {
                if (value instanceof final Number n) {
                    vector.add(n.doubleValue());
                }
            }
            return vector;
        } catch (final Exception e) {
            LOG.fine(() -> "Query embedding failed: " + e.getMessage());
            return List.of();
        }
    }

    private List<Double> readVector(final String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<List<Double>>() {
            });
        } catch (final Exception e) {
            return List.of();
        }
    }

    private double cosineSimilarity(final List<Double> left, final List<Double> right) {
        final int dim = Math.min(left.size(), right.size());
        if (dim == 0) {
            return Double.NaN;
        }

        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < dim; i++) {
            final double a = left.get(i);
            final double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return Double.NaN;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record ScoredChunk(EmbeddingChunk chunk, double score) {
    }

    // ──────────────────────────────────────────────── RAFT retrieval ──────────

    /**
     * Fraction of the total context budget allocated to oracle (high-similarity)
     * documents, following the RAFT paper's P parameter (default 0.8).
     */
    private static final double RAFT_ORACLE_FRACTION = 0.8;

    /**
     * Retrieves project embedding chunks and splits them into oracle (high-score)
     * and distractor (lower-score) sections following the RAFT methodology.
     *
     * <p>
     * All indexed chunks compete by cosine similarity against {@code query}. The
     * top {@code floor(totalLimit * 0.8)} chunks are labelled
     * {@code [ORACLE DOCUMENT n]} and the remaining budget is labelled
     * {@code [DISTRACTOR DOCUMENT n]}. Semantic and graph chunks are scored
     * together so natural relevance drives oracle selection.
     *
     * @param project    the project whose chunks are searched
     * @param query      the retrieval query
     * @param totalLimit total number of chunks to include (oracle + distractor)
     * @return a {@link RaftContext} with labelled sections and a combined string
     */
    public RaftContext retrieveRaftContext(final ProjectFolder project, final String query, final int totalLimit) {
        final RaftContext empty = new RaftContext("", "", "");
        if (project == null || query == null || query.isBlank()
                || !Boolean.TRUE.equals(ollamaConfig.embeddingEnabled())) {
            return empty;
        }

        final String modelName = resolveEmbeddingModelName();
        if (modelName == null || modelName.isBlank()) {
            return empty;
        }

        final List<Double> queryVector = embed(query, modelName);
        if (queryVector.isEmpty()) {
            return empty;
        }

        final List<EmbeddingChunk> chunks = EmbeddingChunk.find("project = ?1", project).list();
        if (chunks.isEmpty()) {
            return empty;
        }

        final List<ScoredChunk> scored = new ArrayList<>();
        for (final EmbeddingChunk chunk : chunks) {
            final List<Double> vector = readVector(chunk.embeddingJson);
            if (vector.isEmpty()) {
                continue;
            }
            final double score = cosineSimilarity(queryVector, vector);
            if (!Double.isFinite(score)) {
                continue;
            }
            scored.add(new ScoredChunk(chunk, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        final int total = Math.max(1, totalLimit);
        final int oracleCount = Math.max(1, (int) Math.floor(total * RAFT_ORACLE_FRACTION));
        final int distractorCount = Math.max(0, total - oracleCount);

        final StringBuilder oracleSb = new StringBuilder(2048);
        for (int i = 0; i < scored.size() && i < oracleCount; i++) {
            final ScoredChunk item = scored.get(i);
            oracleSb.append("[ORACLE DOCUMENT ").append(i + 1).append("]")
                    .append(" source=").append(item.chunk.sourceKey)
                    .append(", similarity=").append(String.format(Locale.ROOT, "%.4f", item.score)).append("\n")
                    .append(item.chunk.chunkText).append("\n\n");
        }

        final StringBuilder distractorSb = new StringBuilder(1024);
        for (int i = oracleCount; i < scored.size() && i < oracleCount + distractorCount; i++) {
            final ScoredChunk item = scored.get(i);
            final int idx = i - oracleCount + 1;
            distractorSb.append("[DISTRACTOR DOCUMENT ").append(idx).append("]")
                    .append(" source=").append(item.chunk.sourceKey)
                    .append(", similarity=").append(String.format(Locale.ROOT, "%.4f", item.score)).append("\n")
                    .append(item.chunk.chunkText).append("\n\n");
        }

        final String oracle = oracleSb.toString().trim();
        final String distractor = distractorSb.toString().trim();
        final String combined = buildRaftCombined(oracle, distractor);
        return new RaftContext(oracle, distractor, combined);
    }

    private static String buildRaftCombined(final String oracle, final String distractor) {
        final StringBuilder sb = new StringBuilder(4096);
        if (oracle != null && !oracle.isBlank()) {
            sb.append("=== ORACLE DOCUMENTS (likely relevant) ===\n\n").append(oracle).append("\n\n");
        }
        if (distractor != null && !distractor.isBlank()) {
            sb.append("=== DISTRACTOR DOCUMENTS (may be irrelevant) ===\n\n").append(distractor);
        }
        return sb.toString().trim();
    }
}