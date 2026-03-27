package ac.uk.sussex.kn253.services.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Toolset that gives the AI assistant read access to project source files.
 *
 * <p>Provides one tool:
 * <ul>
 *   <li>{@code read_file} – read a UTF-8 text file under a project directory,
 *       with an optional line-count limit to avoid overwhelming the context window.</li>
 * </ul>
 *
 * <p>Argument parsing is delegated to {@link ToolArguments}, and all file
 * paths are validated to remain inside the declared project root (path
 * traversal prevention).
 */
@ApplicationScoped
public class ReadToolset implements ToolProvider, ToolExecutor {

    private static final String TOOL_NAME = "read_file";
    private static final int DEFAULT_MAX_LINES = 400;

    private final ToolSpecification readFileSpec = ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Read a UTF-8 text file from a project directory. Optionally limit output to max lines.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("projectDirectory",
                            JsonStringSchema.builder()
                                    .description("Absolute path to project root").build())
                    .addProperty("filePath",
                            JsonStringSchema.builder()
                                    .description("File path relative to the project directory").build())
                    .addProperty("maxLines",
                            JsonStringSchema.builder()
                                    .description("Optional max number of lines to return (default 400)").build())
                    .required("projectDirectory")
                    .required("filePath")
                    .build())
            .build();

    /** Returns the single tool specification exposed by this toolset. */
    public List<ToolSpecification> toolSpecifications() {
        return List.of(readFileSpec);
    }

    /**
     * Returns {@code true} when the given tool name is {@code read_file}.
     *
     * @param toolName the tool name to check.
     */
    public boolean canHandle(final String toolName) {
        return TOOL_NAME.equals(toolName);
    }

    /** {@inheritDoc} */
    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest request) {
        return ToolProviderResult.builder().add(readFileSpec, this).build();
    }

    /** {@inheritDoc} */
    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        if (!canHandle(request.name())) {
            return "Unknown tool for ReadToolset: " + request.name();
        }
        try {
            final Map<String, Object> args = ToolArguments.parse(request.arguments());
            return readFile(args);
        } catch (final IllegalArgumentException e) {
            return "Invalid tool arguments: " + e.getMessage();
        } catch (final Exception e) {
            return "Tool execution failed for " + request.name() + ": " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Tool implementation
    // -------------------------------------------------------------------------

    private String readFile(final Map<String, Object> args) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final Path file = project.resolve(ToolArguments.require(args, "filePath")).normalize();

        if (!file.startsWith(project)) {
            return "Invalid filePath: outside project directory.";
        }
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return "File not found: " + file;
        }

        final int maxLines = ToolArguments.getInt(args, "maxLines", DEFAULT_MAX_LINES);
        if (maxLines < 1) {
            return "Invalid maxLines: must be a positive integer.";
        }

        try {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final int end = Math.min(lines.size(), maxLines);
            return lines.subList(0, end).stream().collect(Collectors.joining("\n"));
        } catch (final IOException e) {
            return "Failed to read file " + file + ": " + e.getMessage();
        }
    }
}
