package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ReadFolderManifestTool implements ToolMacro {

    private final IntrospectToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.READ_FOLDER_MANIFEST.name())
            .description(
                    "Read a specific folder recursively and return an evidence-based folder manifest (discovered files/folders + sampled exact contents from files in that folder tree).\n"
                            + "Use this when the user asks to summarise a folder or subfolder.\n"
                            + "Do NOT use this as a whole-project summary tool; use read_project_manifest for that.\n"
                            + "Do not claim content for files not included in the sampled-content section.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path",
                            JsonStringSchema.builder()
                                    .description(
                                            "Folder path to scan recursively (absolute or relative to cwd). If omitted, uses the current working directory.")
                                    .build())
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
        return support.readFolderManifest(args);
    }
}