package ac.uk.sussex.kn253.services.tools.macro.write;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class CacheProjectKnowledgeTool implements ToolMacro {

    private final WriteToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.CACHE_PROJECT_KNOWLEDGE.name())
            .description(
                    "Append a tagged knowledge note to the persistent project cache. Use this to remember important user requests, constraints, or decisions for later recall.")
            .parameters(JsonObjectSchema.builder()
                    .addProperty("projectDirectory",
                            JsonStringSchema.builder().description("Absolute path to project root").build())
                    .addProperty("tag",
                            JsonStringSchema.builder()
                                    .description("Knowledge tag such as requirements, decisions, bugs, or preferences")
                                    .build())
                    .addProperty("query",
                            JsonStringSchema.builder()
                                    .description("Optional original user query or short paraphrase")
                                    .build())
                    .addProperty("note",
                            JsonStringSchema.builder()
                                    .description("Concise fact or instruction to cache for future recall")
                                    .build())
                    .addProperty("source",
                            JsonStringSchema.builder()
                                    .description("Optional source label such as user_query or assistant_inference")
                                    .build())
                    .required("projectDirectory")
                    .required("tag")
                    .required("note")
                    .build())
            .build();

    public CacheProjectKnowledgeTool(final WriteToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.CACHE_PROJECT_KNOWLEDGE;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        final Path projectDirectory = Path.of(ToolArguments.require(args, "projectDirectory"))
                .toAbsolutePath()
                .normalize();
        if (!Files.isDirectory(projectDirectory)) {
            return "Not a directory: " + projectDirectory;
        }

        final String tag = ToolArguments.require(args, "tag").trim();
        if (tag.isBlank()) {
            return "Invalid tag: must not be blank.";
        }

        final String note = ToolArguments.require(args, "note").trim();
        if (note.isBlank()) {
            return "Invalid note: must not be blank.";
        }

        final String query = ToolArguments.getString(args, "query", "").trim();
        final String source = ToolArguments.getString(args, "source", "user_query").trim();
        return support.cacheProjectKnowledge(projectDirectory, tag, note, query, source);
    }
}