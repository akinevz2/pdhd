package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ListSubdirectoriesTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.LIST_SUBDIRECTORIES.name())
            .description("List immediate sub-folders for a given absolute or relative path.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path", JsonStringSchema.builder().description("Directory path to inspect").build())
                    .required("path")
                    .build())
            .build();

    public ListSubdirectoriesTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.LIST_SUBDIRECTORIES;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.listSubdirectories(args);
    }
}