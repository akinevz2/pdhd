package ac.uk.sussex.kn253.services.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReadToolset implements ToolProvider, ToolExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_LINES = 400;

    private final ToolSpecification readFileToolSpecification = ToolSpecification.builder()
            .name("read_file")
            .description("Read a UTF-8 text file from a project directory. Optionally limit output to max lines.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("projectDirectory",
                            JsonStringSchema.builder().description("Absolute path to project root").build())
                    .addProperty("filePath",
                            JsonStringSchema.builder().description("File path relative to the project directory").build())
                    .addProperty("maxLines",
                            JsonStringSchema.builder().description("Optional max number of lines to return").build())
                    .required("projectDirectory")
                    .required("filePath")
                    .build())
            .build();

    public List<ToolSpecification> toolSpecifications() {
        return List.of(readFileToolSpecification);
    }

    public boolean canHandle(final String toolName) {
        return "read_file".equals(toolName);
    }

    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest request) {
        return ToolProviderResult.builder().add(readFileToolSpecification, this).build();
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        if (!canHandle(request.name())) {
            return "Unknown tool for ReadToolset: " + request.name();
        }
        try {
            final Map<String, Object> args = parseArgs(request.arguments());
            return readFile(args);
        } catch (final IllegalArgumentException e) {
            return "Invalid tool arguments: " + e.getMessage();
        } catch (final Exception e) {
            return "Tool execution failed for " + request.name() + ": " + e.getMessage();
        }
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

    private String readFile(final Map<String, Object> args) {
        final Path project = Path.of(require(args, "projectDirectory")).normalize();
        final Path file = project.resolve(require(args, "filePath")).normalize();
        if (!file.startsWith(project)) {
            return "Invalid filePath: outside project directory.";
        }
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return "File not found: " + file;
        }

        final int maxLines = getInt(args, "maxLines", DEFAULT_MAX_LINES);
        if (maxLines < 1) {
            return "Invalid maxLines value.";
        }

        try {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final int end = Math.min(lines.size(), Math.max(1, maxLines));
            return lines.subList(0, end).stream().collect(Collectors.joining("\n"));
        } catch (final IOException e) {
            return "Failed to read file " + file + ": " + e.getMessage();
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

    private int getInt(final Map<String, Object> args, final String key, final int defaultValue) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (final NumberFormatException e) {
            return -1;
        }
    }
}
