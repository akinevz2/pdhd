package ac.uk.sussex.kn253.tools.introspect;

import java.util.List;
import java.util.stream.Collectors;

import ac.uk.sussex.kn253.entities.fs.Project;
import ac.uk.sussex.kn253.entities.fs.ProjectKnowledge;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ReadProjectKnowledgeToolProvider {

    @Tool("Read cached project knowledge for a specific key. "
            + "Returns the stored content (plain text or JSON).")
    @Transactional
    public String readProjectKnowledge(
            @P("Absolute directory path of the project root") final String projectDirectory,
            @P("Knowledge key to read, e.g. 'summary', 'architecture'") final String key) {

        if (projectDirectory == null || projectDirectory.isBlank()) {
            return "Error: projectDirectory must not be blank.";
        }
        if (key == null || key.isBlank()) {
            return "Error: key must not be blank.";
        }

        final Project project = Project.find("directory = ?1", projectDirectory.trim()).firstResult();
        if (project == null) {
            return "No project found at: " + projectDirectory.trim();
        }

        final ProjectKnowledge pk = ProjectKnowledge.findByProjectAndKey(project, key.trim());
        if (pk == null) {
            return "No knowledge entry found for key '" + key.trim() + "' in project " + projectDirectory.trim();
        }

        return pk.getJsonContent();
    }

    @Tool("List all cached knowledge keys for a project.")
    @Transactional
    public String listProjectKnowledgeKeys(
            @P("Absolute directory path of the project root") final String projectDirectory) {

        if (projectDirectory == null || projectDirectory.isBlank()) {
            return "Error: projectDirectory must not be blank.";
        }

        final Project project = Project.find("directory = ?1", projectDirectory.trim()).firstResult();
        if (project == null) {
            return "No project found at: " + projectDirectory.trim();
        }

        final List<ProjectKnowledge> entries = ProjectKnowledge.find("project = ?1", project).list();
        if (entries.isEmpty()) {
            return "No cached knowledge entries for project " + projectDirectory.trim();
        }

        return entries.stream()
                .map(ProjectKnowledge::getKey)
                .collect(Collectors.joining(", "));
    }
}
