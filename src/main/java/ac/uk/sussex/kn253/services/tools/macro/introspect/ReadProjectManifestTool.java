package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ReadProjectManifestTool implements ToolMacro {

    private final IntrospectToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.READ_PROJECT_MANIFEST.name())
            .description(
                    "Read key project identity files (README, package.json, pom.xml, Cargo.toml, go.mod, requirements.txt, Makefile, etc.) from a directory and inspect src/ recursively (file list + sampled exact contents) to understand the project's purpose and technology stack.\n"
                            + "Use ONLY for summarising or explaining the entire project.\n"
                            + "Do NOT use for summarising individual files or folders—use read_folder_manifest (preferred) or the 'summarize_path' tool from ExploreToolset for those cases.\n"
                            + "If the user asks to summarise a folder or file, do NOT call this tool.\n"
                            + "Parameter 'path' is required unless using the current working directory.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("path",
                            JsonStringSchema.builder()
                                    .description(
                                            "Directory to scan (absolute or relative to cwd). If omitted, uses the current working directory.")
                                    .build())
                    .build())
            .build();

    public ReadProjectManifestTool(final IntrospectToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.READ_PROJECT_MANIFEST;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.readProjectManifest(args);
    }
}