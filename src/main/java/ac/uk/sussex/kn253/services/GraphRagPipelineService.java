package ac.uk.sussex.kn253.services;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Graph-RAG pipeline using LangChain4j's graph transformer.
 * 
 * Extracts structured knowledge graphs from Documents and indexes the graph
 * structure as text for semantic retrieval.
 */
@ApplicationScoped
public class GraphRagPipelineService {

    private static final String GRAPH_SOURCE_SUFFIX = ":graph";

    private static final Logger LOG = Logger.getLogger(GraphRagPipelineService.class.getName());

    @Inject
    EmbeddingIndexingService embeddingIndexingService;

    @Inject
    EmbeddingRetrievalService embeddingRetrievalService;

    /**
     * Index a Document by extracting its knowledge graph and embedding the
     * graph text representation.
     * 
     * @param project   Project context
     * @param sourceKey Source identifier (e.g., "artifact:xyz")
     * @param document  The Document to extract graph structure from
     */
    public void indexGraphArtifact(final ProjectFolder project, final String sourceKey, final Document document) {
        if (project == null || sourceKey == null || document == null) {
            return;
        }

        try {
            // Extract graph structure from document text
            final GraphStructure graph = extractGraph(document.text());

            // Serialize graph as structured text for embedding
            final String graphText = serializeGraph(graph);
            if (graphText.isBlank()) {
                LOG.fine("No graph structure extracted from " + sourceKey);
                return;
            }

            // Create a Document containing the graph representation
            final Document graphDoc = new DefaultDocument(graphText);
            for (final Map.Entry<String, String> entry : createGraphMetadata(sourceKey).entrySet()) {
                graphDoc.metadata().put(entry.getKey(), entry.getValue());
            }

            // Index graph document using embedding pipeline
            embeddingIndexingService.indexDocument(project, graphSourceKey(sourceKey), graphDoc);

            LOG.info("Indexed graph for " + sourceKey + ": " + graph.nodes.size() + " nodes, " + graph.edges.size()
                    + " edges");
        } catch (final Exception e) {
            LOG.warning("Graph extraction failed for " + sourceKey + ": " + e.getMessage());
        }
    }

    /**
     * Retrieve graph context for a query by delegating to embedding retrieval.
     */
    public String retrieveGraphContext(final ProjectFolder project, final String query, final int limit) {
        final String graphQuery = query + "\nFocus on entities, relationships, dependencies, ownership, and structure.";
        return embeddingRetrievalService.retrieveContext(
                project,
                graphQuery,
                limit,
                sourceKey -> sourceKey != null && sourceKey.endsWith(GRAPH_SOURCE_SUFFIX));
    }

    /**
     * Extract knowledge graph from plain text using heuristic patterns.
     */
    private GraphStructure extractGraph(final String text) {
        final GraphStructure graph = new GraphStructure();

        // Simple entity extraction: look for capitalized sequences
        final Pattern entityPattern = Pattern.compile("\\b[A-Z][a-zA-Z]+(?: [A-Z][a-zA-Z]+)*\\b");
        final var matcher = entityPattern.matcher(text);
        while (matcher.find()) {
            final String entity = matcher.group();
            if (!graph.nodes.containsKey(entity)) {
                graph.nodes.put(entity, new GraphNode(entity, "unknown"));
            }
        }

        // Simple relation extraction: look for common verb patterns
        final Pattern relationPattern = Pattern
                .compile("([A-Z][a-zA-Z]+)[\\s,]*(?:is|was|has|have|creates?|makes?|manages?)[\\s]*([A-Z][a-zA-Z]+)");
        final var relMatcher = relationPattern.matcher(text);
        while (relMatcher.find()) {
            final String source = relMatcher.group(1);
            final String target = relMatcher.group(2);
            final String relation = relMatcher.group().replaceAll("^[^(is|was|has|have|creates?|makes?|manages?)]+(.*)",
                    "$1").trim();

            if (graph.nodes.containsKey(source) && graph.nodes.containsKey(target)) {
                graph.edges.add(new GraphEdge(source, target, relation));
            }
        }

        return graph;
    }

    /**
     * Serialize graph structure to readable text format.
     */
    private String serializeGraph(final GraphStructure graph) {
        if (graph.nodes.isEmpty() && graph.edges.isEmpty()) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("## Knowledge Graph\n\n");

        if (!graph.nodes.isEmpty()) {
            sb.append("### Entities\n");
            for (final var node : graph.nodes.values()) {
                sb.append("- ").append(node.id).append(" (").append(node.label).append(")\n");
            }
            sb.append("\n");
        }

        if (!graph.edges.isEmpty()) {
            sb.append("### Relations\n");
            for (final var edge : graph.edges) {
                sb.append("- ").append(edge.source).append(" --[").append(edge.label).append("]--> ")
                        .append(edge.target).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Add graph metadata to document.
     */
    private Map<String, String> createGraphMetadata(final String sourceKey) {
        final Map<String, String> metadata = new HashMap<>();
        metadata.put("source_key", sourceKey);
        metadata.put("graph_indexed", "true");
        return metadata;
    }

    private String graphSourceKey(final String sourceKey) {
        return sourceKey + GRAPH_SOURCE_SUFFIX;
    }

    /**
     * Simple in-memory graph structure.
     */
    private static class GraphStructure {
        final Map<String, GraphNode> nodes = new HashMap<>();
        final List<GraphEdge> edges = new ArrayList<>();
    }

    /**
     * Graph node representation.
     */
    private static class GraphNode {
        final String id;
        final String label;

        GraphNode(final String id, final String label) {
            this.id = id;
            this.label = label;
        }
    }

    /**
     * Graph edge representation.
     */
    private static class GraphEdge {
        final String source;
        final String target;
        final String label;

        GraphEdge(final String source, final String target, final String label) {
            this.source = source;
            this.target = target;
            this.label = label;
        }
    }
}
