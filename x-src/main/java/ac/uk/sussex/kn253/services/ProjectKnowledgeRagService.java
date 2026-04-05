package ac.uk.sussex.kn253.services;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.entities.fs.ProjectKnowledge;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.*;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * RAG (Retrieval-Augmented Generation) service backed by the
 * {@link ProjectKnowledge} table.
 *
 * <p>
 * On startup all previously indexed entries (those with a stored
 * {@code embeddingVector}) are loaded into an {@link InMemoryEmbeddingStore}.
 * When new knowledge is cached, the embedding is computed, persisted on the
 * entity, and added to the in-memory store so retrieval is immediately
 * available without a restart.
 *
 * <p>
 * {@link #retrieveContext(String)} embeds the user query and returns a
 * formatted context block of the top-K most semantically relevant entries
 * for injection into the model prompt.
 */
@ApplicationScoped
public class ProjectKnowledgeRagService {

    private static final Logger LOG = Logger.getLogger(ProjectKnowledgeRagService.class.getName());

    private static final double MIN_SCORE = 0.3;
    private static final int DEFAULT_MAX_RESULTS = 5;

    @Inject
    IChatService chatService;

    @Inject
    ObjectMapper objectMapper;

    private volatile InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private final AtomicInteger indexedCount = new AtomicInteger(0);

    @Transactional
    void onStart(@Observes final StartupEvent event) {
        final InMemoryEmbeddingStore<TextSegment> fresh = new InMemoryEmbeddingStore<>();
        int count = 0;
        for (final ProjectKnowledge pk : ProjectKnowledge.<ProjectKnowledge>listAll()) {
            if (pk.getEmbeddingVector() == null) {
                continue;
            }
            try {
                final float[] vector = objectMapper.readValue(pk.getEmbeddingVector(), float[].class);
                final String dir = resolveDir(pk);
                final String text = buildText(dir, pk.getKey(), pk.getJsonContent());
                fresh.add(String.valueOf(pk.id), Embedding.from(vector), TextSegment.from(text));
                count++;
            } catch (final Exception e) {
                LOG.warning("Skipping corrupted embedding for knowledge id=" + pk.id + ": " + e.getMessage());
            }
        }
        embeddingStore = fresh;
        indexedCount.set(count);
        if (count > 0) {
            LOG.info("RAG: loaded " + count + " indexed knowledge entries.");
        }
    }

    /**
     * Computes and stores an embedding for the given knowledge entry.
     *
     * <p>
     * Sets {@link ProjectKnowledge#setEmbeddingVector} on the entity (the caller
     * must ensure the entity is managed / will be flushed within a transaction).
     * Also updates the in-memory store so retrieval is immediately available.
     *
     * @param entry            the knowledge entry to embed
     * @param projectDirectory directory of the owning project (avoids lazy load)
     */
    public void index(final ProjectKnowledge entry, final String projectDirectory) {
        if (chatService.embeddingModel() == null) {
            LOG.fine("Embedding model unavailable — skipping RAG index for knowledge id=" + entry.id);
            return;
        }
        try {
            final String text = buildText(projectDirectory, entry.getKey(), entry.getJsonContent());
            final Embedding embedding = chatService.embeddingModel().embed(TextSegment.from(text)).content();
            entry.setEmbeddingVector(objectMapper.writeValueAsString(embedding.vector()));
            embeddingStore.add(String.valueOf(entry.id), embedding, TextSegment.from(text));
            indexedCount.incrementAndGet();
        } catch (final Exception e) {
            LOG.warning("Failed to embed knowledge id=" + entry.id + ": " + e.getMessage());
        }
    }

    /**
     * Returns a formatted block of the most semantically relevant knowledge
     * entries for the given query, ready for prompt injection.
     *
     * @param query user query
     * @return formatted context string, or empty string when nothing relevant
     *         was found or embedding is unavailable
     */
    public String retrieveContext(final String query) {
        return retrieveContext(query, DEFAULT_MAX_RESULTS);
    }

    public String retrieveContext(final String query, final int maxResults) {
        if (chatService.embeddingModel() == null || indexedCount.get() == 0) {
            return "";
        }
        try {
            final Embedding queryEmbedding = chatService.embeddingModel().embed(TextSegment.from(query)).content();
            final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(MIN_SCORE)
                    .build();
            final EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
            if (result.matches().isEmpty()) {
                return "";
            }
            final StringBuilder sb = new StringBuilder("--- Relevant project knowledge ---\n");
            for (final EmbeddingMatch<TextSegment> match : result.matches()) {
                sb.append(match.embedded().text()).append('\n');
            }
            sb.append("--- End of project knowledge ---\n\n");
            return sb.toString();
        } catch (final Exception e) {
            LOG.warning("RAG retrieval failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * Returns {@code true} when the embedding model is available and at least one
     * entry is indexed.
     */
    public boolean isAvailable() {
        return chatService.embeddingModel() != null && indexedCount.get() > 0;
    }

    public int getIndexedCount() {
        return indexedCount.get();
    }

    /**
     * Deletes all knowledge entries and clears the in-memory retrieval index.
     *
     * @return number of deleted knowledge rows
     */
    @Transactional
    public long clearKnowledgeBase() {
        final long deleted = ProjectKnowledge.deleteAll();
        embeddingStore = new InMemoryEmbeddingStore<>();
        indexedCount.set(0);
        return deleted;
    }

    private static String resolveDir(final ProjectKnowledge pk) {
        return (pk.getProject() != null && pk.getProject().getDirectory() != null)
                ? pk.getProject().getDirectory()
                : "unknown";
    }

    static String buildText(final String projectDir, final String key, final String content) {
        return "[project:" + projectDir + "][key:" + key + "] " + content;
    }
}
