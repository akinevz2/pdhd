package ac.uk.sussex.kn253.services;

import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.repository.ProjectKnowledge;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Persistence helper for project/folder summary artifacts stored in
 * ProjectKnowledge.
 * 
 * Automatically indexes stored content for semantic retrieval via
 * Document-based
 * embedding pipeline.
 */
@ApplicationScoped
public class ProjectKnowledgeSummaryStoreService {

    private static final Logger LOG = Logger.getLogger(ProjectKnowledgeSummaryStoreService.class.getName());

    @Inject
    EmbeddingIndexingService embeddingIndexingService;

    @Inject
    GraphRagPipelineService graphRagPipelineService;

    @Transactional
    public void upsert(final ProjectFolder project, final String key, final String content) {
        final Instant now = Instant.now();
        ProjectKnowledge existing = ProjectKnowledge.findByProjectAndKey(project, key);
        if (existing == null) {
            existing = new ProjectKnowledge();
            existing.setProject(project);
            existing.setKey(key);
            existing.setCreatedAt(now);
            existing.setEmbeddingVector(null);
        }
        existing.setJsonContent(content == null ? "" : content);
        existing.setUpdatedAt(now);
        existing.persist();

        // Index content for semantic retrieval
        if (content != null && !content.isBlank()) {
            indexContentAsDocument(project, key, content);
        }
    }

    /**
     * Index content by converting to Document and embedding.
     */
    private void indexContentAsDocument(final ProjectFolder project, final String key, final String content) {
        try {
            // Create a Document from the content
            final Document doc = new DefaultDocument(content);
            // Add metadata
            doc.metadata().put("source", key);
            doc.metadata().put("project", project.getDirectory());

            // Index via embedding pipeline
            embeddingIndexingService.indexDocument(project, key, doc);

            // Also extract and index graph structure
            graphRagPipelineService.indexGraphArtifact(project, key, doc);
        } catch (final Exception e) {
            LOG.warning("Failed to index content for " + key + ": " + e.getMessage());
        }
    }

    public String read(final ProjectFolder project, final String key) {
        final ProjectKnowledge entry = ProjectKnowledge.findByProjectAndKey(project, key);
        return entry == null ? null : entry.getJsonContent();
    }

    public List<ProjectKnowledge> listByPrefix(final ProjectFolder project, final String keyPrefix) {
        return ProjectKnowledge.find("project = ?1 and key like ?2 order by key asc", project, keyPrefix + "%")
                .list();
    }
}
