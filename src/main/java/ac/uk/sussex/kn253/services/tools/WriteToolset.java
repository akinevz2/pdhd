package ac.uk.sussex.kn253.services.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WriteToolset implements ToolProvider, ToolExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final boolean DEFAULT_APPEND = false;

    private final List<ToolSpecification> toolSpecifications = List.of(
            writeFileSpec(),
            createReportSpec(),
            createTimelineSpec(),
            createPlanSpec(),
            createTodoSpec());

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    public boolean canHandle(final String toolName) {
        return toolSpecifications.stream().anyMatch(spec -> spec.name().equals(toolName));
    }

    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest request) {
        final ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (final ToolSpecification spec : toolSpecifications) {
            builder.add(spec, this);
        }
        return builder.build();
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        try {
            final Map<String, Object> args = parseArgs(request.arguments());
            return switch (request.name()) {
                case "write_file" -> writeFileTool(args);
                case "create_report" -> createReport(args);
                case "create_timeline" -> createTimeline(args);
                case "create_plan" -> createPlan(args);
                case "create_todo_in_project" -> createTodoInProject(args);
                default -> "Unknown tool for WriteToolset: " + request.name();
            };
        } catch (final IllegalArgumentException e) {
            return "Invalid tool arguments: " + e.getMessage();
        } catch (final Exception e) {
            return "Tool execution failed for " + request.name() + ": " + e.getMessage();
        }
    }

    private ToolSpecification writeFileSpec() {
        return ToolSpecification.builder()
                .name("write_file")
                .description("Write a UTF-8 text file within the project directory.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory", JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("filePath", JsonStringSchema.builder().description("Path relative to project root").build())
                        .addProperty("content", JsonStringSchema.builder().description("File content to write").build())
                        .addProperty("append", JsonStringSchema.builder().description("Optional true/false; default false").build())
                        .required("projectDirectory")
                        .required("filePath")
                        .required("content")
                        .build())
                .build();
    }

    private ToolSpecification createReportSpec() {
        return ToolSpecification.builder()
                .name("create_report")
                .description("Create a markdown report under <project>/.pdhd/reports.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory", JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("title", JsonStringSchema.builder().description("Report title").build())
                        .addProperty("content", JsonStringSchema.builder().description("Report markdown content").build())
                        .required("projectDirectory")
                        .required("title")
                        .required("content")
                        .build())
                .build();
    }

    private ToolSpecification createTimelineSpec() {
        return ToolSpecification.builder()
                .name("create_timeline")
                .description("Create a timeline markdown under <project>/.pdhd/timelines.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory", JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("title", JsonStringSchema.builder().description("Timeline title").build())
                        .addProperty("milestones", JsonArraySchema.builder().description("Array of milestone strings in chronological order").build())
                        .required("projectDirectory")
                        .required("title")
                        .required("milestones")
                        .build())
                .build();
    }

    private ToolSpecification createPlanSpec() {
        return ToolSpecification.builder()
                .name("create_plan")
                .description("Create an execution plan markdown under <project>/.pdhd/plans.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory", JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("title", JsonStringSchema.builder().description("Plan title").build())
                        .addProperty("steps", JsonArraySchema.builder().description("Ordered list of plan steps").build())
                        .required("projectDirectory")
                        .required("title")
                        .required("steps")
                        .build())
                .build();
    }

    private ToolSpecification createTodoSpec() {
        return ToolSpecification.builder()
                .name("create_todo_in_project")
                .description("Append a todo entry to <project>/TODO.md.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory", JsonStringSchema.builder().description("Absolute path to project root").build())
                        .addProperty("todo", JsonStringSchema.builder().description("Todo text to append").build())
                        .required("projectDirectory")
                        .required("todo")
                        .build())
                .build();
    }

    private Map<String, Object> parseArgs(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (final Exception e) {
            return Map.of();
        }
    }

    private String writeFileTool(final Map<String, Object> args) {
        final Path project = Path.of(require(args, "projectDirectory")).normalize();
        final Path output = project.resolve(require(args, "filePath")).normalize();
        if (!output.startsWith(project)) {
            return "Invalid filePath: outside project directory.";
        }
        final String content = require(args, "content");
        final boolean append = getBoolean(args, "append", DEFAULT_APPEND);
        return writeFile(output, content, append, "File written");
    }

    private String createReport(final Map<String, Object> args) {
        final Path project = Path.of(require(args, "projectDirectory")).normalize();
        final String title = require(args, "title");
        final String content = require(args, "content");
        final Path output = project.resolve(".pdhd/reports/" + slug(title) + ".md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid report path.";
        }

        final String body = "# " + title + "\n\n" + content + "\n\nGenerated: " + Instant.now() + "\n";
        return writeFile(output, body, false, "Report created");
    }

    private String createTimeline(final Map<String, Object> args) {
        final Path project = Path.of(require(args, "projectDirectory")).normalize();
        final String title = require(args, "title");
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
        final Path project = Path.of(require(args, "projectDirectory")).normalize();
        final String title = require(args, "title");
        final List<String> steps = toStringList(args.get("steps"));
        final Path output = project.resolve(".pdhd/plans/" + slug(title) + ".md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid plan path.";
        }

        final StringBuilder body = new StringBuilder();
        body.append("# ").append(title).append("\n\n");
        body.append("Date: ").append(LocalDate.now()).append("\n\n");
        for (int i = 0; i < steps.size(); i++) {
            body.append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        return writeFile(output, body.toString(), false, "Plan created");
    }

    private String createTodoInProject(final Map<String, Object> args) {
        final Path project = Path.of(require(args, "projectDirectory")).normalize();
        final String todo = require(args, "todo");
        final Path output = project.resolve("TODO.md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid TODO path.";
        }

        final String line = "- [ ] " + todo + " (created " + LocalDate.now() + ")\n";
        return writeFile(output, line, true, "TODO added");
    }

    private String writeFile(final Path output, final String content, final boolean append, final String prefix) {
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

    private String require(final Map<String, Object> args, final String key) {
        final String val = getString(args, key, "");
        if (val.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return val;
    }

    private String getString(final Map<String, Object> args, final String key, final String defaultValue) {
        final Object val = args.get(key);
        return val == null ? defaultValue : String.valueOf(val);
    }

    private boolean getBoolean(final Map<String, Object> args, final String key, final boolean defaultValue) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private List<String> toStringList(final Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        for (final Object item : raw) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    private String slug(final String title) {
        final String normalized = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "untitled" : normalized;
    }
}
