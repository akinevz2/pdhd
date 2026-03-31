package ac.uk.sussex.kn253.services;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.model.ProjectKnowledge;
import ac.uk.sussex.kn253.schema.SchemaKeys;
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

    public static final String METADATA_TITLE = "Current folder metadata";

    public static final String VALUE_NONE = "none";
    public static final String VALUE_NO_TAGS = "(none)";
    public static final String VALUE_NEXT_READ_PROJECT_KNOWLEDGE = "read_project_knowledge";
    public static final String VALUE_ASSISTANT_ACTION_POLICY = "When proposing one follow-up action, emit exactly one assistant-action fenced code block instead of plain-text next-steps or further-inspection sections.";
    public static final String VALUE_ASSISTANT_ACTION_BLOCK_LANGUAGE = "assistant-action";
    public static final String VALUE_ASSISTANT_ACTION_BLOCK_TEMPLATE = "{\"label\":\"<short button label>\",\"prompt\":\"<single actionable follow-up user prompt>\"}";

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

        final Map<String, Object> signals = new LinkedHashMap<>();
        signals.put(SchemaKeys.CWD, cwd.toString());
        addAssistantActionSignals(signals);

        if (project == null) {
            signals.put(SchemaKeys.PROJECT, VALUE_NONE);
            signals.put(SchemaKeys.HAS_HISTORY, false);
            signals.put(SchemaKeys.TAG_COUNT, 0);
            signals.put(SchemaKeys.TAGS, VALUE_NO_TAGS);
            return toSignalBlock(METADATA_TITLE, signals);
        }

        final List<ProjectKnowledge> knowledge = ProjectKnowledge.<ProjectKnowledge>list("project", project).stream()
                .sorted(java.util.Comparator.comparing(ProjectKnowledge::getKey))
                .toList();
        final boolean hasHistory = !knowledge.isEmpty();
        final String tags = knowledge.isEmpty()
                ? VALUE_NO_TAGS
                : knowledge.stream()
                        .map(entry -> entry.getKey() + "(" + countEntries(entry.getJsonContent()) + ")")
                        .collect(Collectors.joining(", "));

        signals.put(SchemaKeys.FOLDER_ID, project.id);
        signals.put(SchemaKeys.FOLDER_DIR, project.getDirectory());
        signals.put(SchemaKeys.HAS_GIT, project.getGitRepository() != null);
        signals.put(SchemaKeys.TAG_COUNT, knowledge.size());
        signals.put(SchemaKeys.TAGS, tags);
        signals.put(SchemaKeys.HAS_HISTORY, hasHistory);
        if (hasHistory) {
            signals.put(SchemaKeys.NEXT, VALUE_NEXT_READ_PROJECT_KNOWLEDGE);
        }
        return toSignalBlock(METADATA_TITLE, signals);
    }

    private void addAssistantActionSignals(final Map<String, Object> signals) {
        signals.put(SchemaKeys.ASSISTANT_ACTION_POLICY, VALUE_ASSISTANT_ACTION_POLICY);
        signals.put(SchemaKeys.ASSISTANT_ACTION_BLOCK_LANGUAGE, VALUE_ASSISTANT_ACTION_BLOCK_LANGUAGE);
        signals.put(SchemaKeys.ASSISTANT_ACTION_BLOCK_TEMPLATE, VALUE_ASSISTANT_ACTION_BLOCK_TEMPLATE);
    }

    private static String toSignalBlock(final String title, final Map<String, Object> signals) {
        try {
            return title + ":\n" + OBJECT_MAPPER.writeValueAsString(signals);
        } catch (final IOException e) {
            return title + ":\n{}";
        }
    }

    private static int countEntries(final String rawJson) {
        try {
            final JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            final JsonNode entries = root.get(SchemaKeys.ENTRIES);
            if (entries != null && entries.isArray()) {
                return entries.size();
            }
        } catch (final IOException ignored) {
            // Fall through.
        }
        return 0;
    }
}