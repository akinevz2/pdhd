package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ReadProjectKnowledgeTool implements ToolMacro {

    private final IntrospectToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.READ_PROJECT_KNOWLEDGE.name())
            .description(
                    "Read cached tagged project knowledge remembered from earlier user queries or prior analysis. Use this to recall stored constraints, decisions, requirements, or bug notes before repeating work.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("projectDirectory",
                            JsonStringSchema.builder()
                                    .description(
                                            "Project directory to read knowledge for (absolute or relative to cwd).")
                                    .build())
                    .addProperty("tag",
                            JsonStringSchema.builder()
                                    .description(
                                            "Optional knowledge tag to read in full. If omitted, returns available tags.")
                                    .build())
                    .required("projectDirectory")
                    .build())
            .build();

    public ReadProjectKnowledgeTool(final IntrospectToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.READ_PROJECT_KNOWLEDGE;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.readProjectKnowledge(args);
    }
}