package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class SearchPathsTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.SEARCH_PATHS.name())
            .description(definition().description())
            .parameters(JsonObjectSchema.builder()
                    .addProperty("query",
                            JsonStringSchema.builder()
                                    .description("Required partial file or directory name to search for.")
                                    .build())
                    .addProperty("path",
                            JsonStringSchema.builder()
                                    .description(
                                            "Optional search root directory. Defaults to the current working directory.")
                                    .build())
                    .addProperty("maxDepth",
                            JsonStringSchema.builder()
                                    .description("Optional maximum search depth from the root (0-8, default 4).")
                                    .build())
                    .addProperty("limit",
                            JsonStringSchema.builder()
                                    .description("Optional maximum number of matches to return (1-50, default 12).")
                                    .build())
                    .addProperty("includeFiles",
                            JsonStringSchema.builder()
                                    .description("Optional boolean flag to include file matches. Defaults to true.")
                                    .build())
                    .addProperty("includeDirectories",
                            JsonStringSchema.builder()
                                    .description(
                                            "Optional boolean flag to include directory matches. Defaults to true.")
                                    .build())
                    .required("query")
                    .build())
            .build();

    public SearchPathsTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.SEARCH_PATHS;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.searchPaths(args);
    }
}