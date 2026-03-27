package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class GetCurrentWorkingDirectoryTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.GET_CURRENT_WORKING_DIRECTORY.name())
            .description("Return the current working directory as an absolute path.")
            .parameters(JsonObjectSchema.builder().build())
            .build();

    public GetCurrentWorkingDirectoryTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.GET_CURRENT_WORKING_DIRECTORY;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.getCwd();
    }
}