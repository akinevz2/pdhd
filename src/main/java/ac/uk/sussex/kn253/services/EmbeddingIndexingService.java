package ac.uk.sussex.kn253.services;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import ac.uk.sussex.kn253.repository.*;
import dev.langchain4j.data.document.Document;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Indexes Documents into semantic embeddings by chunking and calling Ollama.
 * 
 * Chunking strategy: fixed-size chunks (1800 chars) with overlap (200 chars) to
 * preserve context across chunk boundaries.
 * 
 * Idempotency: Uses content hash + model name + chunker version to detect when
 * re-indexing is needed. Only new/changed chunks are embedded.
 */
@ApplicationScoped
public class EmbeddingIndexingService {

    private static final Logger LOG = Logger.getLogger(EmbeddingIndexingService.class.getName());

    private static final String CHUNKER_VERSION = "1.0";
    private static final int CHUNK_SIZE = 1800;
    private static final int CHUNK_OVERLAP = 200;

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    OllamaConfig ollamaConfig;

    @Inject
    OllamaRuntimeEndpointService runtimeEndpointService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Index a Document by chunking it, computing embeddings, and storing chunks.
     * 
     * @param project   The project context for these chunks
     * @param sourceKey Logical identifier for the document source (e.g., file path,
     *                  or "summary:project")
     * @param document  The langchain4j Document to index
     */
    @Transactional
    public void indexDocument(final ProjectFolder project, final String sourceKey, final Document document) {
        if (project == null || sourceKey == null || document == null) {
            return;
        }

        final String content = document.text();
        if (content == null || content.isBlank()) {
            return;
        }

        if (!Boolean.TRUE.equals(ollamaConfig.embeddingEnabled())) {
            LOG.info("Embedding disabled; skipping indexing of " + sourceKey);
            return;
        }

        final LLMSettings settings = modelConfigService.getCurrentSettings();
        if (settings == null || settings.getEmbeddingModelName() == null
                || settings.getEmbeddingModelName().isBlank()) {
            LOG.warning("No embedding model configured; skipping " + sourceKey);
            return;
        }
        final String modelName = settings.getEmbeddingModelName();

        final String contentHash = computeHash(content);
        final List<String> chunks = chunkDocument(content);

        int chunkIndex = 0;
        for (final String chunkText : chunks) {
            // Check if this chunk already exists with same hash/model/strategy
            final EmbeddingChunk existing = EmbeddingChunk.find(
                    "project = ?1 and sourceKey = ?2 and chunkIndex = ?3",
                    project, sourceKey, chunkIndex).firstResult();

            if (existing != null && existing.contentHash.equals(contentHash)
                    && existing.modelName.equals(modelName)
                    && existing.chunkerVersion.equals(CHUNKER_VERSION)) {
                // Already indexed with same parameters; skip
                chunkIndex++;
                continue;
            }

            // Embed the chunk
            final List<Double> embedding = embed(chunkText, modelName);
            if (embedding.isEmpty()) {
                LOG.warning("Failed to embed chunk " + chunkIndex + " of " + sourceKey);
                chunkIndex++;
                continue;
            }

            // Convert embedding to JSON and persist
            final String embeddingJson = serializeEmbedding(embedding);

            if (existing != null) {
                // Update existing chunk
                existing.chunkText = chunkText;
                existing.contentHash = contentHash;
                existing.modelName = modelName;
                existing.chunkerVersion = CHUNKER_VERSION;
                existing.embeddingJson = embeddingJson;
                existing.indexedAt = java.time.Instant.now();
                existing.persist();
            } else {
                // Create new chunk
                final EmbeddingChunk chunk = new EmbeddingChunk(project, sourceKey, chunkIndex,
                        chunkText, contentHash, modelName, CHUNKER_VERSION, embeddingJson);
                chunk.persist();
            }

            chunkIndex++;
        }

        EmbeddingChunk.delete("project = ?1 and sourceKey = ?2 and chunkIndex >= ?3", project, sourceKey, chunkIndex);

        LOG.info("Indexed " + chunkIndex + " chunks for " + sourceKey);
    }

    /**
     * Break content into overlapping chunks of fixed size.
     */
    private List<String> chunkDocument(final String content) {
        final List<String> chunks = new ArrayList<>();
        if (content.length() <= CHUNK_SIZE) {
            chunks.add(content);
            return chunks;
        }

        int pos = 0;
        while (pos < content.length()) {
            final int end = Math.min(pos + CHUNK_SIZE, content.length());
            chunks.add(content.substring(pos, end));
            if (end >= content.length()) {
                break;
            }
            pos = end - CHUNK_OVERLAP;
        }
        return chunks;
    }

    /**
     * Call Ollama /api/embeddings endpoint.
     */
    private List<Double> embed(final String text, final String model) {
        try {
            final LLMSettings settings = modelConfigService.getCurrentSettings();
            if (settings == null) {
                return List.of();
            }
            final String baseEndpoint = runtimeEndpointService.resolvePersistedOrActive(settings.getBaseUrl());
            if (baseEndpoint == null) {
                return List.of();
            }
            final String endpoint = baseEndpoint + (baseEndpoint.endsWith("/") ? "api/embeddings" : "/api/embeddings");

            final String requestBody = "{\"model\":\"" + model + "\",\"input\":\"" + escapeJson(text) + "\"}";
            final HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            final HttpResponse<String> response = HttpClient.newHttpClient().send(req,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warning("Ollama returned " + response.statusCode() + ": " + response.body());
                return List.of();
            }

            final java.util.Map<String, Object> json = objectMapper.readValue(response.body(),
                    new TypeReference<java.util.Map<String, Object>>() {
                    });
            @SuppressWarnings("rawtypes")
            final java.util.List list = (java.util.List) json.get("embedding");
            @SuppressWarnings("unchecked")
            final java.util.List<Double> embedding = (java.util.List<Double>) list;
            return embedding != null ? embedding : List.of();
        } catch (final Exception e) {
            LOG.warning("Failed to embed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Compute SHA-256 hash of content.
     */
    private String computeHash(final String content) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder();
            for (final byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (final Exception e) {
            LOG.warning("Hash computation failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * Serialize embedding vector to JSON array.
     */
    private String serializeEmbedding(final List<Double> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (final Exception e) {
            LOG.warning("Failed to serialize embedding: " + e.getMessage());
            return "[]";
        }
    }

    /**
     * Simple JSON string escaper.
     */
    private String escapeJson(final String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
