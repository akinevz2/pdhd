package ac.uk.sussex.kn253.services.tools.macro.write;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;

public class CreatePlanTool implements ToolMacro {

    private final WriteToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.CREATE_PLAN.name())
            .description("Create an execution plan markdown under <project>/.pdhd/plans.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("projectDirectory",
                            JsonStringSchema.builder().description("Absolute path to project root").build())
                    .addProperty("title",
                            JsonStringSchema.builder().description("Plan title").build())
                    .addProperty("content",
                            JsonStringSchema.builder()
                                    .description("Optional full markdown content; if supplied, steps is ignored")
                                    .build())
                    .addProperty("steps",
                            JsonArraySchema.builder()
                                    .description("Ordered list of plan steps (used when content is absent)")
                                    .build())
                    .required("projectDirectory")
                    .required("title")
                    .build())
            .build();

    public CreatePlanTool(final WriteToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.CREATE_PLAN;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final String title = ToolArguments.require(args, "title");
        final String content = ToolArguments.getString(args, "content", "").trim();
        final List<String> steps = support.toStringList(args.get("steps"));
        final Path output = project.resolve(".pdhd/plans/" + support.slug(title) + ".md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid plan path.";
        }
        if (!content.isBlank()) {
            final String body = content.endsWith("\n") ? content : content + "\n";
            return support.writeFile(output, body, false, "Plan created");
        }

        final StringBuilder body = new StringBuilder();
        body.append("# ").append(title).append("\n\n");
        body.append("Date: ").append(LocalDate.now()).append("\n\n");
        for (int i = 0; i < steps.size(); i++) {
            body.append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        return support.writeFile(output, body.toString(), false, "Plan created");
    }
}