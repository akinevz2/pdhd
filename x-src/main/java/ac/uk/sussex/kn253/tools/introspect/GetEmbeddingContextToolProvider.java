package ac.uk.sussex.kn253.tools.introspect;

import ac.uk.sussex.kn253.services.ProjectKnowledgeRagService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GetEmbeddingContextToolProvider {

    @Inject
    ProjectKnowledgeRagService ragService;

    @Tool("Retrieve semantically relevant project knowledge for a query using vector similarity search. "
            + "Use this before answering questions about projects to surface previously cached facts.")
    public String getEmbeddingContext(
            @P("The question or topic to find relevant project knowledge for") final String query,
            @P("Maximum number of results to return (1–10)") final int maxResults) {

        if (query == null || query.isBlank()) {
            return "Error: query must not be blank.";
        }

        final int bounded = Math.max(1, Math.min(maxResults, 10));
        final String context = ragService.retrieveContext(query, bounded);
        if (context.isBlank()) {
            if (!ragService.isAvailable()) {
                return "Semantic retrieval unavailable — no embedding model or no indexed knowledge yet.";
            }
            return "No relevant knowledge found for: " + query;
        }
        return context;
    }

    @Tool("Check whether semantic knowledge retrieval (RAG) is available and how many entries are indexed.")
    public String getRagStatus() {
        if (!ragService.isAvailable()) {
            return "RAG unavailable — embedding model not configured or no knowledge has been cached yet.";
        }
        return "RAG active. " + ragService.getIndexedCount() + " knowledge entries indexed.";
    }
}
