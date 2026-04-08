package ac.uk.sussex.kn253.repository;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

/**
 * Persists an individual chunk of a Document with its embedding vector.
 * Chunks are created during embedding indexing and retrieved via semantic
 * search.
 *
 * Unique constraint ensures idempotent re-indexing: chunks with same project,
 * source_key, and chunk_index are updated rather than duplicated.
 */
@Entity
@Table(name = "embedding_chunk", uniqueConstraints = {
                @UniqueConstraint(columnNames = { "project_id", "source_key", "chunk_index" })
})
public class EmbeddingChunk extends PanacheEntity {

        @ManyToOne(optional = false, fetch = FetchType.LAZY)
        public ProjectFolder project;

        /**
         * Logical source identifier: typically the relative path or key identifying
         * where this chunk came from (e.g., "src/Main.java", "README.md", or
         * "summary:project").
         */
        @Column(nullable = false, length = 512)
        public String sourceKey;

        /**
         * Sequential chunk index within the source document, starting from 0.
         */
        @Column(nullable = false)
        public int chunkIndex;

        /**
         * The textual content of this chunk.
         */
        @Column(nullable = false, columnDefinition = "TEXT")
        public String chunkText;

        /**
         * SHA-256 hash of the original source content. Used to detect when a source
         * has changed and needs re-embedding.
         */
        @Column(name = "content_hash", nullable = false, length = 64)
        public String contentHash;

        /**
         * Name of the embedding model used to generate the vector.
         * Stored to detect model changes for re-embedding.
         */
        @Column(nullable = false, length = 128)
        public String modelName;

        /**
         * Version identifier of the chunking strategy used.
         * Stored to detect chunking strategy changes for re-embedding.
         */
        @Column(nullable = false, length = 32)
        public String chunkerVersion;

        /**
         * The embedding vector serialized as JSON.
         * Typically a JSON array of doubles: [0.123, -0.456, ...]
         */
        @Column(nullable = false, columnDefinition = "TEXT")
        public String embeddingJson;

        /**
         * Timestamp when this chunk was created or last updated.
         */
        @Column(nullable = false)
        public Instant indexedAt;

        public EmbeddingChunk() {
        }

        public EmbeddingChunk(final ProjectFolder project, final String sourceKey, final int chunkIndex,
                        final String chunkText, final String contentHash, final String modelName,
                        final String chunkerVersion, final String embeddingJson) {
                this.project = project;
                this.sourceKey = sourceKey;
                this.chunkIndex = chunkIndex;
                this.chunkText = chunkText;
                this.contentHash = contentHash;
                this.modelName = modelName;
                this.chunkerVersion = chunkerVersion;
                this.embeddingJson = embeddingJson;
                this.indexedAt = Instant.now();
        }
}
