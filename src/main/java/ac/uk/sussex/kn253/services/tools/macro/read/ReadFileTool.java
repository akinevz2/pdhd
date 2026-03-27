package ac.uk.sussex.kn253.services.tools.macro.read;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ReadFileTool implements ToolMacro {

    private static final int DEFAULT_MAX_LINES = 400;

    private final ReadToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.READ_FILE.name())
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

    public ReadFileTool() {
        this(new ReadToolSupport());
    }

    ReadFileTool(final ReadToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.READ_FILE;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
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
            final String result = lines.subList(0, end).stream().collect(Collectors.joining("\n"));

            // Cache the full file content (not just the limited output)
            try {
                final String fullContent = String.join("\n", lines);
                final String relativePath = project.relativize(file).toString();
                support.cacheFileContent(project, relativePath, fullContent);
            } catch (final Exception ignored) {
                // Caching failure should not break the tool
            }

            return result;
        } catch (final IOException e) {
            return "Failed to read file " + file + ": " + e.getMessage();
        }
    }
}