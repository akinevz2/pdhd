package ac.uk.sussex.kn253.services.tools.macro.write;

import java.nio.file.Path;
import java.util.Map;

import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class AppendProjectTodoTool implements ToolMacro {

    private final WriteToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.APPEND_PROJECT_TODO.name())
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

    public AppendProjectTodoTool(final WriteToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.APPEND_PROJECT_TODO;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final String todo = ToolArguments.require(args, "todo");
        final Path output = project.resolve("TODO.md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid TODO path.";
        }
        return support.writeFile(project, output, support.todoLine(todo), true, "TODO added");
    }
}