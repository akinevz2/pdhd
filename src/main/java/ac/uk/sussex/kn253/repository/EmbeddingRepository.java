package ac.uk.sussex.kn253.repository;

import java.util.List;

import ac.uk.sussex.kn253.model.EmbeddingEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Data access for embeddings.
 */
@ApplicationScoped
public class EmbeddingRepository implements PanacheRepository<EmbeddingEntity> {

    /**
     * Find recent embeddings for a session.
     */
    public List<EmbeddingEntity> findRecentBySession(final String sessionId, final int limit) {
        return find(
                "sessionId = ?1 order by timestamp desc",
                sessionId).range(0, limit - 1).list();
    }

    /**
     * Find all embeddings for a session.
     */
    public List<EmbeddingEntity> findBySession(final String sessionId) {
        return find("sessionId", sessionId).list();
    }

    /**
     * Delete all embeddings for a session.
     */
    public long deleteBySession(final String sessionId) {
        return delete("sessionId", sessionId);
    }

    /**
     * Count embeddings for a session.
     */
    public long countBySession(final String sessionId) {
        return count("sessionId", sessionId);
    }

    /**
     * Find embeddings by source.
     */
    public List<EmbeddingEntity> findBySource(final String sourceId) {
        return find("sourceId", sourceId).list();
    }

    /**
     * Delete embeddings by source.
     */
    public long deleteBySource(final String sourceId) {
        return delete("sourceId", sourceId);
    }
}
