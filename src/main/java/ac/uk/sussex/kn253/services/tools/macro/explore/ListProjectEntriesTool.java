package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ListProjectEntriesTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.LIST_PROJECT_ENTRIES.name())
            .description("List files and folders in a project's directory, optionally under a relative subpath.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("projectDirectory",
                            JsonStringSchema.builder().description("Absolute path to the project root directory")
                                    .build())
                    .addProperty("relativePath",
                            JsonStringSchema.builder().description("Optional sub-directory inside the project").build())
                    .required("projectDirectory")
                    .build())
            .build();

    public ListProjectEntriesTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.LIST_PROJECT_ENTRIES;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.listProjectEntries(args);
    }
}