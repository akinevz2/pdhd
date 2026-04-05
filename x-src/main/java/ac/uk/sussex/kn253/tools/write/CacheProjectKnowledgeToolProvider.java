package ac.uk.sussex.kn253.tools.write;

import java.time.Instant;

import ac.uk.sussex.kn253.entities.fs.Project;
import ac.uk.sussex.kn253.entities.fs.ProjectKnowledge;
import ac.uk.sussex.kn253.services.ProjectKnowledgeRagService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CacheProjectKnowledgeToolProvider {

    @Inject
    ProjectKnowledgeRagService ragService;

    @Tool("Cache or update structured knowledge about a project under a named key. "
            + "Use this to persist summaries, architecture notes, dependency maps, TODOs, or any "
            + "other reusable project fact. Cached entries are automatically indexed for semantic retrieval.")
    @Transactional
    public String cacheProjectKnowledge(
            @P("Absolute directory path of the project root") final String projectDirectory,
            @P("Knowledge key, e.g. 'summary', 'architecture', 'dependencies', 'todos'") final String key,
            @P("Content to cache — plain text or JSON") final String content) {

        if (projectDirectory == null || projectDirectory.isBlank()) {
            return "Error: projectDirectory must not be blank.";
        }
        if (key == null || key.isBlank()) {
            return "Error: key must not be blank.";
        }
        if (content == null || content.isBlank()) {
            return "Error: content must not be blank.";
        }

        final String dir = projectDirectory.trim();
        final String k = key.trim();

        Project project = Project.find("directory = ?1", dir).firstResult();
        if (project == null) {
            project = new Project(null, dir, null, null);
            project.persist();
        }

        final Instant now = Instant.now();
        ProjectKnowledge pk = ProjectKnowledge.findByProjectAndKey(project, k);
        if (pk == null) {
            pk = new ProjectKnowledge();
            pk.setProject(project);
            pk.setKey(k);
            pk.setCreatedAt(now);
        }
        pk.setJsonContent(content.trim());
        pk.setUpdatedAt(now);
        pk.persistAndFlush();

        ragService.index(pk, dir);

        return "Cached knowledge '" + k + "' for project " + dir + "."
                + (ragService.isAvailable() ? " Indexed for semantic retrieval." : "");
    }
}
