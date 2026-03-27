package ac.uk.sussex.kn253.services.tools.macro.write;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;

public class CreateTimelineTool implements ToolMacro {

    private final WriteToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.CREATE_TIMELINE.name())
            .description("Create a timeline markdown under <project>/.pdhd/timelines.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("projectDirectory",
                            JsonStringSchema.builder().description("Absolute path to project root").build())
                    .addProperty("title",
                            JsonStringSchema.builder().description("Timeline title").build())
                    .addProperty("milestones",
                            JsonArraySchema.builder()
                                    .description("Array of milestone strings in chronological order")
                                    .build())
                    .required("projectDirectory")
                    .required("title")
                    .required("milestones")
                    .build())
            .build();

    public CreateTimelineTool(final WriteToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.CREATE_TIMELINE;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final String title = ToolArguments.require(args, "title");
        final List<String> milestones = support.toStringList(args.get("milestones"));
        final Path output = project.resolve(".pdhd/timelines/" + support.slug(title) + ".md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid timeline path.";
        }

        final StringBuilder body = new StringBuilder();
        body.append("# ").append(title).append("\n\n");
        body.append("Generated: ").append(Instant.now()).append("\n\n");
        for (int i = 0; i < milestones.size(); i++) {
            body.append(i + 1).append(". ").append(milestones.get(i)).append("\n");
        }
        return support.writeFile(output, body.toString(), false, "Timeline created");
    }
}