package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class OpenWorkspaceCanvasTool implements ToolMacro {

    private final IntrospectToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.OPEN_WORKSPACE_CANVAS.name())
            .description(definition().description())
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path",
                            JsonStringSchema.builder()
                                    .description(
                                            "Target path to open in canvas (file or directory). If omitted, uses the current working directory.")
                                    .build())
                    .build())
            .build();

    public OpenWorkspaceCanvasTool(final IntrospectToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.OPEN_WORKSPACE_CANVAS;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.openWorkspaceCanvas(args);
    }
}