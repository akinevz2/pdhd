package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ReadFolderManifestTool implements ToolMacro {

    private final IntrospectToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.READ_FOLDER_MANIFEST.name())
            .description(definition().description())
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path",
                            JsonStringSchema.builder()
                                    .description(
                                            "Folder path to scan recursively (absolute or relative to cwd). If omitted, uses the current working directory.")
                                    .build())
                    .required("path")
                    .build())
            .build();

    public ReadFolderManifestTool(final IntrospectToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.READ_FOLDER_MANIFEST;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        // ToolMacro contract is map-based; extract typed path and delegate.
        return support.readFolderManifest(ToolArguments.require(args, "path"));
    }
}