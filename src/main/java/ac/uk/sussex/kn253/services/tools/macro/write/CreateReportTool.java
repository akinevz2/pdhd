package ac.uk.sussex.kn253.services.tools.macro.write;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class CreateReportTool implements ToolMacro {

    private final WriteToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.CREATE_REPORT.name())
            .description("Create a markdown report under <project>/.pdhd/reports.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("projectDirectory",
                            JsonStringSchema.builder().description("Absolute path to project root").build())
                    .addProperty("title",
                            JsonStringSchema.builder().description("Report title").build())
                    .addProperty("content",
                            JsonStringSchema.builder().description("Report markdown content").build())
                    .required("projectDirectory")
                    .required("title")
                    .required("content")
                    .build())
            .build();

    public CreateReportTool(final WriteToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.CREATE_REPORT;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        final Path project = Path.of(ToolArguments.require(args, "projectDirectory")).normalize();
        final String title = ToolArguments.require(args, "title");
        final String content = ToolArguments.require(args, "content");
        final Path output = project.resolve(".pdhd/reports/" + support.slug(title) + ".md").normalize();
        if (!output.startsWith(project)) {
            return "Invalid report path.";
        }
        final String body = "# " + title + "\n\n" + content + "\n\nGenerated: " + Instant.now() + "\n";
        return support.writeFile(project, output, body, false, "Report created");
    }
}