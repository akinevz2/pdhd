package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class SummarizePathTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.SUMMARIZE_PATH.name())
            .description("Provide a concise summary of a file or directory path.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path",
                            JsonStringSchema.builder().description("File or directory path to summarize").build())
                    .required("path")
                    .build())
            .build();

    public SummarizePathTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.SUMMARIZE_PATH;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.summarizePath(args);
    }
}