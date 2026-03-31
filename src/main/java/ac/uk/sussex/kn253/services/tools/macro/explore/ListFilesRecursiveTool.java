package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ListFilesRecursiveTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.LIST_FILES_RECURSIVE.name())
            .description(definition().description())
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path", JsonStringSchema.builder().description("Directory path to inspect").build())
                    .build())
            .build();

    public ListFilesRecursiveTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.LIST_FILES_RECURSIVE;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.listFilesRecursive(args);
    }
}