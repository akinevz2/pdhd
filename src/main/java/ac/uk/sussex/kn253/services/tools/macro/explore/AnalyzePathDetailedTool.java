package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.ToolMacro;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacroDefinition;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacros;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class AnalyzePathDetailedTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.ANALYZE_PATH_DETAILED.name())
            .description(definition().description())
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path",
                            JsonStringSchema.builder().description("File or directory path to analyze").build())
                    .required("path")
                    .build())
            .build();

    public AnalyzePathDetailedTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.ANALYZE_PATH_DETAILED;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.analyzePathDetailed(args);
    }
}