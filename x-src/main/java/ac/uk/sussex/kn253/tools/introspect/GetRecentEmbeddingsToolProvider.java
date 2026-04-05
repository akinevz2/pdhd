package ac.uk.sussex.kn253.tools.introspect;

import java.util.List;

import ac.uk.sussex.kn253.entities.fs.ProjectKnowledge;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class GetRecentEmbeddingsToolProvider {

    @Tool("List the most recently cached knowledge entries across all projects.")
    @Transactional
    public String getRecentKnowledge(
            @P("Maximum number of recent entries to return (1–20)") final int limit) {

        final int bounded = Math.max(1, Math.min(limit, 20));
        final List<ProjectKnowledge> recent = ProjectKnowledge
                .find("ORDER BY updatedAt DESC")
                .page(0, bounded)
                .list();

        if (recent.isEmpty()) {
            return "No knowledge entries cached yet.";
        }

        final StringBuilder sb = new StringBuilder();
        for (final ProjectKnowledge pk : recent) {
            final String dir = (pk.getProject() != null && pk.getProject().getDirectory() != null)
                    ? pk.getProject().getDirectory()
                    : "unknown";
            sb.append("[project:").append(dir)
                    .append("][key:").append(pk.getKey())
                    .append("][updated:").append(pk.getUpdatedAt()).append("]\n");
            sb.append(pk.getJsonContent()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
