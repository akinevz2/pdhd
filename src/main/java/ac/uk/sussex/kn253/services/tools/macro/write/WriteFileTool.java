package ac.uk.sussex.kn253.services.tools.macro.write;

import java.nio.file.Path;
import java.util.Map;

import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class WriteFileTool implements ToolMacro {

    private static final boolean DEFAULT_APPEND = false;

    private final WriteToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.WRITE_FILE.name())
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

    public WriteFileTool(final WriteToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.WRITE_FILE;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final Path output = project.resolve(ToolArguments.require(args, "filePath")).normalize();
        if (!output.startsWith(project)) {
            return "Invalid filePath: outside project directory.";
        }
        final String content = ToolArguments.require(args, "content");
        final boolean append = ToolArguments.getBoolean(args, "append", DEFAULT_APPEND);
        return support.writeFile(output, content, append, "File written");
    }
}