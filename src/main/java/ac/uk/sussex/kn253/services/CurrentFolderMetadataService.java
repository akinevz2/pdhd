package ac.uk.sussex.kn253.services;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.model.ProjectKnowledge;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Builds a lightweight metadata block for the current folder so each assistant
 * turn has stable context about the tagged folder and any cached knowledge.
 */
@ApplicationScoped
public class CurrentFolderMetadataService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    WorkingDirectoryService workingDirectoryService;

    @Inject
    ProjectDiscoveryService projectDiscoveryService;

    /**
     * Builds a metadata block for the current working directory.
     *
     * <p>
     * The block is intentionally lightweight: it exposes the tagged folder id,
     * git status, cached knowledge tags, and a concrete boolean flag that tells
     * the model whether this folder has been worked on previously.
     */
    @Transactional
    public String buildPromptContext() {
        try {
            projectDiscoveryService.discoverFromCwd();
        } catch (final IOException ignored) {
            // Best-effort; metadata can still be built from current DB state.
        }

        final Path cwd = workingDirectoryService.getCurrentWorkingDirectory().toAbsolutePath().normalize();
        final Project project = Project.find("directory", cwd.toString()).firstResult();

        final StringBuilder sb = new StringBuilder();
        sb.append("Current folder metadata:\n");
        sb.append("- cwd: ").append(cwd).append("\n");

        if (project == null) {
            sb.append("- current project: (none)\n");
            return sb.toString().trim();
        }

        final List<ProjectKnowledge> knowledge = ProjectKnowledge.<ProjectKnowledge>list("project", project).stream()
                .sorted(java.util.Comparator.comparing(ProjectKnowledge::getKey))
                .toList();
        final boolean previouslyWorkedOnHere = !knowledge.isEmpty();
        final String tags = knowledge.isEmpty()
                ? "(none)"
                : knowledge.stream()
                        .map(entry -> entry.getKey() + "(" + countEntries(entry.getJsonContent()) + ")")
                        .collect(Collectors.joining(", "));

        sb.append("- taggedFolderId: ").append(project.id).append("\n");
        sb.append("- taggedFolderDirectory: ").append(project.getDirectory()).append("\n");
        sb.append("- folderHasGitRepository: ").append(project.getGitRepository() != null).append("\n");
        sb.append("- cachedKnowledgeTagCount: ").append(knowledge.size()).append("\n");
        sb.append("- cachedKnowledgeTags: ").append(tags).append("\n");
        sb.append("- previouslyWorkedOnHere: ").append(previouslyWorkedOnHere).append("\n");
        sb.append(
                "- meaning: previouslyWorkedOnHere is true only when this tagged folder has one or more cached project knowledge records.\n");
        if (previouslyWorkedOnHere) {
            sb.append(
                    "- guidance: prefer read_project_knowledge before repeating investigation that may already be cached.\n");
        }
        return sb.toString().trim();
    }

    private static int countEntries(final String rawJson) {
        try {
            final JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            final JsonNode entries = root.get("entries");
            if (entries != null && entries.isArray()) {
                return entries.size();
            }
        } catch (final IOException ignored) {
            // Fall through.
        }
        return 0;
    }
}