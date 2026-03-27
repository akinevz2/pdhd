package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class GetPathInfoTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.GET_PATH_INFO.name())
            .description("Return basic metadata for a path (exists, type, readable, writability, absolute path).")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path", JsonStringSchema.builder().description("Absolute or relative path").build())
                    .required("path")
                    .build())
            .build();

    public GetPathInfoTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.GET_PATH_INFO;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.pathInfo(args);
    }
}