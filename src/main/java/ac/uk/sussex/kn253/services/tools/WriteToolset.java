package ac.uk.sussex.kn253.services.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.model.ProjectKnowledge;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.service.tool.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Toolset that gives the AI assistant the ability to write files and create
 * structured project artefacts.
 *
 *
 * <p>
 * All output paths are validated to remain inside the declared project root
 * (path traversal prevention). Argument parsing is delegated to
 * {@link ToolArguments}.
 */
@ApplicationScoped
public class WriteToolset implements ToolProvider, ToolExecutor {
    private static final String WRITE_FILE_TOOL = "write_file";
    private static final String CREATE_REPORT_TOOL = "create_report";
    private static final String CREATE_TIMELINE_TOOL = "create_timeline";
    private static final String CREATE_PLAN_TOOL = "create_plan";
    private static final String CACHE_PROJECT_KNOWLEDGE_TOOL = "cache_project_knowledge";

    private static final boolean DEFAULT_APPEND = false;
    private static final String APPEND_PROJECT_TODO = "append_project_todo";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Legacy tool name alias for backward compatibility.
     */
    private static final Map<String, String> LEGACY_ALIASES = Map.of(
            "create_todo_in_project", APPEND_PROJECT_TODO);

    /**
     * Tool specifications for all available tools.
     * Includes both core tools and legacy aliases.
     */
    private final List<ToolSpecification> toolSpecifications = List.of(
            writeFileSpec(),
            createReportSpec(),
            createTimelineSpec(),
            createPlanSpec(),
            createTodoSpec(),
            cacheProjectKnowledgeSpec());

    /** Returns all tool specifications exposed by this toolset. */
    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    /**
     * Returns {@code true} if this toolset can handle a tool with the given name,
     * including legacy aliases.
     *
     * @param toolName the raw tool name from the model response.
     */
    public boolean canHandle(final String toolName) {
        final String canonical = canonical(toolName);
        return toolSpecifications.stream().anyMatch(spec -> spec.name().equals(canonical));
    }

    /** {@inheritDoc} */
    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest request) {
        final ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (final ToolSpecification spec : toolSpecifications) {
            builder.add(spec, this);
        }
        return builder.build();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        try {
            final Map<String, Object> args = ToolArguments.parse(request.arguments());
            return dispatch(canonical(request.name()), args);
        } catch (final IllegalArgumentException e) {
            return "Invalid tool arguments: " + e.getMessage();
        } catch (final Exception e) {
            return "Tool execution failed for " + request.name() + ": " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    private String dispatch(final String toolName, final Map<String, Object> args) {
        return switch (toolName) {
            case WRITE_FILE_TOOL -> writeFileTool(args);
            case CREATE_REPORT_TOOL -> createReport(args);
            case CREATE_TIMELINE_TOOL -> createTimeline(args);
            case CREATE_PLAN_TOOL -> createPlan(args);
            case APPEND_PROJECT_TODO -> appendProjectTodo(args);
            case CACHE_PROJECT_KNOWLEDGE_TOOL -> cacheProjectKnowledge(args);
            default -> "Unknown tool for WriteToolset: " + toolName;
        };
    }

    // -------------------------------------------------------------------------
    // Tool implementations
    // -------------------------------------------------------------------------

    private String writeFileTool(final Map<String, Object> args) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final Path output = project.resolve(ToolArguments.require(args, "filePath")).normalize();
        if (!output.startsWith(project)) {
            return "Invalid filePath: outside project directory.";
        }
        final String content = ToolArguments.require(args, "content");
        final boolean append = ToolArguments.getBoolean(args, "append", DEFAULT_APPEND);
        return writeFile(output, content, append, "File written");
    }

    private String createReport(final Map<String, Object> args) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final String title = ToolArguments.require(args, "title");
        final String content = ToolArguments.require(args, "content");
        final Path output = project.resolve(".pdhd/reports/" + slug(title) + ".md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid report path.";
        }
        final String body = "# " + title + "\n\n" + content + "\n\nGenerated: " + Instant.now() + "\n";
        return writeFile(output, body, false, "Report created");
    }

    private String createTimeline(final Map<String, Object> args) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final String title = ToolArguments.require(args, "title");
        final List<String> milestones = toStringList(args.get("milestones"));
        final Path output = project.resolve(".pdhd/timelines/" + slug(title) + ".md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid timeline path.";
        }
        final StringBuilder body = new StringBuilder();
        body.append("# ").append(title).append("\n\n");
        body.append("Generated: ").append(Instant.now()).append("\n\n");
        for (int i = 0; i < milestones.size(); i++) {
            body.append(i + 1).append(". ").append(milestones.get(i)).append("\n");
        }
        return writeFile(output, body.toString(), false, "Timeline created");
    }

    private String createPlan(final Map<String, Object> args) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final String title = ToolArguments.require(args, "title");
        final String content = ToolArguments.getString(args, "content", "").trim();
        final List<String> steps = toStringList(args.get("steps"));
        final Path output = project.resolve(".pdhd/plans/" + slug(title) + ".md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid plan path.";
        }
        if (!content.isBlank()) {
            final String body = content.endsWith("\n") ? content : content + "\n";
            return writeFile(output, body, false, "Plan created");
        }
        final StringBuilder body = new StringBuilder();
        body.append("# ").append(title).append("\n\n");
        body.append("Date: ").append(LocalDate.now()).append("\n\n");
        for (int i = 0; i < steps.size(); i++) {
            body.append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        return writeFile(output, body.toString(), false, "Plan created");
    }

    private String appendProjectTodo(final Map<String, Object> args) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final String todo = ToolArguments.require(args, "todo");
        final Path output = project.resolve("TODO.md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid TODO path.";
        }
        final String line = "- [ ] " + todo + " (created " + LocalDate.now() + ")\n";
        return writeFile(output, line, true, "TODO added");
    }

    private String cacheProjectKnowledge(final Map<String, Object> args) {
        final Path projectDirectory = Path.of(ToolArguments.require(args, "projectDirectory"))
                .toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(projectDirectory)) {
            return "Not a directory: " + projectDirectory;
        }

        final String tag = ToolArguments.require(args, "tag").trim();
        if (tag.isBlank()) {
            return "Invalid tag: must not be blank.";
        }

        final String note = ToolArguments.require(args, "note").trim();
        if (note.isBlank()) {
            return "Invalid note: must not be blank.";
        }

        final String query = ToolArguments.getString(args, "query", "").trim();
        final String source = ToolArguments.getString(args, "source", "user_query").trim();
        final Instant now = Instant.now();

        final Project project = resolveOrCreateProject(projectDirectory);
        ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project, tag);

        final ObjectNode root;
        final ArrayNode entries;
        if (knowledge == null || knowledge.getJsonContent() == null || knowledge.getJsonContent().isBlank()) {
            root = OBJECT_MAPPER.createObjectNode();
            root.put("tag", tag);
            root.put("projectDirectory", projectDirectory.toString());
            entries = root.putArray("entries");
        } else {
            root = parseKnowledgeObject(knowledge.getJsonContent(), tag, projectDirectory);
            final JsonNode existingEntries = root.get("entries");
            if (!(existingEntries instanceof final ArrayNode arrayNode)) {
                entries = root.putArray("entries");
            } else {
                entries = arrayNode;
            }
        }

        final ObjectNode entryNode = entries.addObject();
        entryNode.put("timestamp", now.toString());
        entryNode.put("source", source.isBlank() ? "user_query" : source);
        if (!query.isBlank()) {
            entryNode.put("query", query);
        }
        entryNode.put("note", note);

        final String jsonContent;
        try {
            jsonContent = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (final IOException e) {
            return "Failed to serialize project knowledge for " + projectDirectory + ": " + e.getMessage();
        }

        if (knowledge == null) {
            knowledge = new ProjectKnowledge(null, project, tag, jsonContent, now, now);
            knowledge.persist();
        } else {
            knowledge.setJsonContent(jsonContent);
            knowledge.setUpdatedAt(now);
        }

        return "Cached project knowledge: project=" + projectDirectory
                + " tag=" + tag
                + " entries=" + entries.size();
    }

    // -------------------------------------------------------------------------
    // Low-level file writer
    // -------------------------------------------------------------------------

    /**
     * Writes {@code content} to {@code output}, creating parent directories as
     * needed.
     *
     * @param output  destination path.
     * @param content UTF-8 content to write.
     * @param append  {@code true} to append; {@code false} to truncate.
     * @param prefix  success message prefix.
     * @return a descriptive result string.
     */
    private String writeFile(
            final Path output,
            final String content,
            final boolean append,
            final String prefix) {
        try {
            Files.createDirectories(output.getParent());
            if (append) {
                Files.writeString(output, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(output, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            return prefix + ": " + output;
        } catch (final IOException e) {
            return "Failed to write file " + output + ": " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a raw argument value to a {@code List<String>}.
     * Accepts both a JSON array (list of objects) and a plain text string where
     * each non-blank line is treated as one item (stripping leading list markers).
     *
     * @param value raw argument object from the parsed JSON map.
     * @return mutable list of string items, never {@code null}.
     */
    private static List<String> toStringList(final Object value) {
        if (value instanceof final String rawString) {
            return rawString.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.replaceFirst("^[-*]\\s+", ""))
                    .map(line -> line.replaceFirst("^\\d+\\.\\s+", ""))
                    .toList();
        }
        if (!(value instanceof final List<?> raw)) {
            return List.of();
        }
        final List<String> out = new ArrayList<>(raw.size());
        for (final Object item : raw) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    /**
     * Converts a title string to a filesystem-safe slug.
     * Non-alphanumeric characters are replaced with hyphens; leading and
     * trailing hyphens are trimmed.
     *
     * @param title human-readable title.
     * @return a lowercase, hyphen-separated slug, or {@code "untitled"} when
     *         the title produces an empty string.
     */
    private static String slug(final String title) {
        final String normalized = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "untitled" : normalized;
    }

    private static ObjectNode parseKnowledgeObject(
            final String rawJson,
            final String tag,
            final Path projectDirectory) {
        try {
            final JsonNode parsed = OBJECT_MAPPER.readTree(rawJson);
            if (parsed instanceof final ObjectNode objectNode) {
                return objectNode;
            }
        } catch (final IOException ignored) {
            // Fall back to a fresh structured object below.
        }

        final ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("tag", tag);
        root.put("projectDirectory", projectDirectory.toString());
        root.putArray("entries");
        return root;
    }

    private static Project resolveOrCreateProject(final Path projectDirectory) {
        final String directory = projectDirectory.toString();
        final Project existing = Project.find("directory", directory).firstResult();
        if (existing != null) {
            return existing;
        }

        final Project created = new Project(null, directory, null, null);
        created.persist();
        return created;
    }

    /**
     * Returns the canonical tool name for {@code toolName}, applying legacy
     * aliases where appropriate.
     *
     * @param toolName the raw name from the model response; may be {@code null}.
     * @return the canonical name, or an empty string for null/blank input.
     */
    private static String canonical(final String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "";
        }
        return LEGACY_ALIASES.getOrDefault(toolName, toolName);
    }

    // -------------------------------------------------------------------------
    // Tool specification builders
    // -------------------------------------------------------------------------

    private static ToolSpecification writeFileSpec() {
        return ToolSpecification.builder()
                .name(WRITE_FILE_TOOL)
                .description("Write a UTF-8 text file within the project directory.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory",
                                JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("filePath",
                                JsonStringSchema.builder().description("Path relative to project root").build())
                        .addProperty("content",
                                JsonStringSchema.builder().description("File content to write").build())
                        .addProperty("append",
                                JsonStringSchema.builder()
                                        .description("Optional true/false; default false (overwrite)").build())
                        .required("projectDirectory")
                        .required("filePath")
                        .required("content")
                        .build())
                .build();
    }

    private static ToolSpecification createReportSpec() {
        return ToolSpecification.builder()
                .name(CREATE_REPORT_TOOL)
                .description("Create a markdown report under <project>/.pdhd/reports.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory",
                                JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("title",
                                JsonStringSchema.builder().description("Report title").build())
                        .addProperty("content",
                                JsonStringSchema.builder().description("Report markdown content").build())
                        .required("projectDirectory")
                        .required("title")
                        .required("content")
                        .build())
                .build();
    }

    private static ToolSpecification createTimelineSpec() {
        return ToolSpecification.builder()
                .name(CREATE_TIMELINE_TOOL)
                .description("Create a timeline markdown under <project>/.pdhd/timelines.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory",
                                JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("title",
                                JsonStringSchema.builder().description("Timeline title").build())
                        .addProperty("milestones",
                                JsonArraySchema.builder()
                                        .description("Array of milestone strings in chronological order")
                                        .build())
                        .required("projectDirectory")
                        .required("title")
                        .required("milestones")
                        .build())
                .build();
    }

    private static ToolSpecification createPlanSpec() {
        return ToolSpecification.builder()
                .name(CREATE_PLAN_TOOL)
                .description("Create an execution plan markdown under <project>/.pdhd/plans.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory",
                                JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("title",
                                JsonStringSchema.builder().description("Plan title").build())
                        .addProperty("content",
                                JsonStringSchema.builder()
                                        .description("Optional full markdown content; if supplied, steps is ignored")
                                        .build())
                        .addProperty("steps",
                                JsonArraySchema.builder()
                                        .description("Ordered list of plan steps (used when content is absent)")
                                        .build())
                        .required("projectDirectory")
                        .required("title")
                        .build())
                .build();
    }

    private static ToolSpecification createTodoSpec() {
        return ToolSpecification.builder()
                .name(APPEND_PROJECT_TODO)
                .description("Append a todo entry to <project>/TODO.md.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory",
                                JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("todo",
                                JsonStringSchema.builder().description("Todo text to append").build())
                        .required("projectDirectory")
                        .required("todo")
                        .build())
                .build();
    }

    private static ToolSpecification cacheProjectKnowledgeSpec() {
        return ToolSpecification.builder()
                .name(CACHE_PROJECT_KNOWLEDGE_TOOL)
                .description(
                        "Append a tagged knowledge note to the persistent project cache. "
                                + "Use this to remember important user requests, constraints, or decisions for later recall.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory",
                                JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("tag",
                                JsonStringSchema.builder()
                                        .description(
                                                "Knowledge tag such as requirements, decisions, bugs, or preferences")
                                        .build())
                        .addProperty("query",
                                JsonStringSchema.builder()
                                        .description("Optional original user query or short paraphrase")
                                        .build())
                        .addProperty("note",
                                JsonStringSchema.builder()
                                        .description("Concise fact or instruction to cache for future recall")
                                        .build())
                        .addProperty("source",
                                JsonStringSchema.builder()
                                        .description("Optional source label such as user_query or assistant_inference")
                                        .build())
                        .required("projectDirectory")
                        .required("tag")
                        .required("note")
                        .build())
                .build();
    }
}
