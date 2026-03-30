package ac.uk.sussex.kn253.services;

import java.io.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.model.*;
import ac.uk.sussex.kn253.repository.EmbeddingRepository;
import ac.uk.sussex.kn253.schema.ToolSupport;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.ResolutionException;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Service for generating, storing, and retrieving embeddings.
 * 
 * <p>
 * Embeddings enable semantic search and content retrieval by converting
 * text into numerical vector representations.
 */
@ApplicationScoped
public class EmbeddingService {

    private static final Logger LOG = Logger.getLogger(EmbeddingService.class);

    @Inject
    EmbeddingRepository embeddingRepository;

    @Inject
    OllamaConfigService ollamaConfigService;

    private EmbeddingModel embeddingModel;
    private boolean enabled = false;
    private int embeddingDimension = 384;

    @PostConstruct
    void init() {
        try {
            final var config = ollamaConfigService.load();
            final String embeddingModel = config.getEmbeddingModel();
            String embeddingBaseUrl = config.getEmbeddingBaseUrl();
            // Default to chat model's baseUrl if embedding baseUrl not explicitly set
            if (embeddingBaseUrl == null || embeddingBaseUrl.isBlank()) {
                embeddingBaseUrl = config.getBaseUrl();
            }
            final Integer embeddingDimension = config.getEmbeddingDimension();
            final Boolean embeddingEnabled = config.getEmbeddingEnabled();

            if (embeddingEnabled != null && embeddingEnabled &&
                    embeddingModel != null && !embeddingModel.isBlank() &&
                    embeddingBaseUrl != null && !embeddingBaseUrl.isBlank()) {

                this.embeddingModel = OllamaEmbeddingModel.builder()
                        .baseUrl(embeddingBaseUrl)
                        .modelName(embeddingModel)
                        .build();
                this.enabled = true;
                this.embeddingDimension = embeddingDimension != null ? embeddingDimension : 384;
                LOG.infof("Embedding service initialized: model=%s, url=%s, dimension=%d",
                        embeddingModel, embeddingBaseUrl, this.embeddingDimension);
            } else {
                LOG.info("Embedding service disabled - not fully configured");
            }
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to initialize embedding service");
        }
    }

    /**
     * Check if embedding service is enabled.
     */
    public boolean isEnabled() {
        return enabled && embeddingModel != null;
    }

    /**
     * Get the current embedding dimension.
     */
    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    /**
     * Generate an embedding for text.
     * Throws when embeddings are unavailable or cannot be generated.
     */
    public EmbeddingVector generateEmbedding(final String text, final String memoryId) {
        if (!isEnabled()) {
            throw new IllegalStateException("Embedding service is not enabled");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding text must not be blank");
        }

        try {
            LOG.debugf("Generating embedding for text (length=%d)", text.length());

            final var response = embeddingModel.embed(text);
            if (response == null || response.content() == null || response.content().vector() == null
                    || response.content().vector().length == 0) {
                throw new IllegalStateException("Embedding model returned an empty response");
            }

            final float[] vector = response.content().vector();
            final String embeddingId = UUID.randomUUID().toString();

            return new EmbeddingVector(
                    embeddingId, text,
                    System.currentTimeMillis(),
                    ToolSupport.VALUE_USER_INPUT,
                    embeddingId, memoryId, vector);
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to generate embedding", e);
        }
    }

    /**
     * Store an embedding in the database.
     */
    @Transactional
    public void storeEmbedding(
            final EmbeddingVector embedding,
            final String sourceId,
            final String sourceType,
            final String sessionId) throws ResolutionException {
        if (embedding == null) {
            return;
        }

        try {
            final byte[] vectorData = floatArrayToBytes(embedding.vector());

            String textSnippet = embedding.text();
            if (textSnippet != null && textSnippet.length() > 500) {
                textSnippet = textSnippet.substring(0, 500);
            }

            final EmbeddingEntity entity = new EmbeddingEntity(
                    embedding.id(),
                    sessionId,
                    textSnippet,
                    embedding.text(),
                    sourceType,
                    sourceId,
                    embedding.timestamp(),
                    embedding.memoryId(),
                    embedding.dimension(), vectorData);

            embeddingRepository.persist(entity);
            LOG.debugf("Stored embedding: id=%s, source=%s", embedding.id(), sourceId);
        } catch (final IOException e) {
            throw new ResolutionException(e);
        }
    }

    /**
     * Search for semantically similar embeddings.
     */
    @Transactional
    public List<EmbeddingMatch> search(final String query, final int limit, final String sessionId) {
        if (!isEnabled() || query == null || query.isBlank() || sessionId == null) {
            return List.of();
        }

        try {
            // Generate embedding for query
            final EmbeddingVector queryVector = generateEmbedding(query, sessionId);

            // Retrieve all embeddings for the session
            final List<EmbeddingEntity> stored = embeddingRepository.findBySession(sessionId);
            if (stored.isEmpty()) {
                return List.of();
            }

            // Compute similarities
            final List<EmbeddingMatch> results = stored.stream()
                    .<EmbeddingMatch>mapMulti((entity, consumer) -> addSimilarityMatch(queryVector, entity, consumer))
                    .sorted((a, b) -> Float.compare(b.similarity(), a.similarity()))
                    .limit(limit)
                    .collect(Collectors.toList());

            LOG.debugf("Search returned %d results from %d stored embeddings",
                    results.size(), stored.size());
            return results;
        } catch (final Exception e) {
            LOG.warnf(e, "Search failed: %s", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get recent embeddings for a session.
     */
    @Transactional
    public List<EmbeddingMatch> getRecentEmbeddings(final String sessionId, final int limit) {
        if (sessionId == null) {
            return List.of();
        }

        try {
            final List<EmbeddingEntity> entities = embeddingRepository.findRecentBySession(sessionId, limit);
            return entities.stream()
                    .map(this::toRecentEmbeddingMatch)
                    .collect(Collectors.toList());
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to retrieve recent embeddings: %s", e.getMessage());
            return List.of();
        }
    }

    /**
     * Clear all embeddings for a session.
     */
    @Transactional
    public long clearSession(final String sessionId) {
        if (sessionId == null) {
            return 0;
        }

        try {
            final long count = embeddingRepository.deleteBySession(sessionId);
            LOG.infof("Cleared %d embeddings for session %s", count, sessionId);
            return count;
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to clear embeddings: %s", e.getMessage());
            return 0;
        }
    }

    /**
     * Clear all embeddings (use with caution).
     */
    @Transactional
    public long clearAll() {
        try {
            final long count = embeddingRepository.deleteAll();
            LOG.infof("Cleared all %d embeddings", count);
            return count;
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to clear all embeddings: %s", e.getMessage());
            return 0;
        }
    }

    // Helper methods for vector serialization

    private void addSimilarityMatch(
            final EmbeddingVector queryVector,
            final EmbeddingEntity entity,
            final Consumer<EmbeddingMatch> consumer) {
        try {
            final float[] vector = bytesToFloatArray(entity.getVectorData());
            final EmbeddingVector storedVector = new EmbeddingVector(entity, vector);
            final float similarity = queryVector.cosineSimilarity(storedVector);
            consumer.accept(new EmbeddingMatch(entity, similarity));
        } catch (final Exception e) {
            LOG.warnf(e, "Failed to compute similarity for embedding %s", entity.getId());
        }
    }

    private EmbeddingMatch toRecentEmbeddingMatch(final EmbeddingEntity entity) {
        return new EmbeddingMatch(entity);
    }

    private byte[] floatArrayToBytes(final float[] floats) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(floats.length * 4);
        final DataOutputStream dos = new DataOutputStream(baos);
        for (final float f : floats) {
            dos.writeFloat(f);
        }
        dos.close();
        return baos.toByteArray();
    }

    private float[] bytesToFloatArray(final byte[] bytes) throws IOException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final DataInputStream dis = new DataInputStream(bais);
        final float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = dis.readFloat();
        }
        dis.close();
        return floats;
    }
}
