package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ChangeWorkingDirectoryTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.CHANGE_WORKING_DIRECTORY.name())
            .description(definition().description())
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path",
                            JsonStringSchema.builder().description("Directory path to navigate to").build())
                    .required("path")
                    .build())
            .build();

    public ChangeWorkingDirectoryTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.CHANGE_WORKING_DIRECTORY;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.changeCwd(args);
    }
}