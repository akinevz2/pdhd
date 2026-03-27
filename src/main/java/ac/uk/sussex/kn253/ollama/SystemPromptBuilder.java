package ac.uk.sussex.kn253.ollama;

import java.util.List;
import java.util.Locale;

import ac.uk.sussex.kn253.services.ToolService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

/**
 * Builds the effective system prompt that is sent to the Ollama model on every
 * conversation turn.
 *
 * <p>
 * For models that support the native tool-calling API (e.g. the llama3.2
 * family) the user-configured system prompt is returned unchanged. For models
 * that do not (e.g. qwen2.5-coder), a hidden tool-calling addendum is appended
 * that instructs the model to emit tool invocations as XML
 * ({@code <tool_call>…</tool_call>}) so that {@link ToolCallParser} can
 * intercept and execute them.
 *
 * <p>
 * This class is a stateless utility; all methods are static.
 */
public final class SystemPromptBuilder {

    private SystemPromptBuilder() {
        // utility class
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the effective system prompt for a conversation turn.
     *
     * <p>
     * Appends a tool-calling addendum when:
     * <ol>
     * <li>a {@link ToolService} with registered tools is present, <em>and</em></li>
     * <li>the configured model is not in the llama3.2 family (which has
     * native tool-calling support).</li>
     * </ol>
     *
     * @param systemPrompt        user-configured system instruction.
     * @param configuredModelName the model name string taken from
     *                            {@link OllamaConfig#modelName()}.
     * @param toolService         the tool service; may be {@code null}.
     * @return the effective system prompt, never {@code null}.
     */
    public static String build(
            final String systemPrompt,
            final String toolSystemPrompt,
            final String configuredModelName,
            final ToolService toolService) {
        if (toolService == null || toolService.toolSpecifications().isEmpty()) {
            return systemPrompt;
        }
        final String promptWithPolicy = buildToolUsePolicy()
                + "\n\nMain assistant instructions:\n"
                + systemPrompt
                + "\n\nTool agent instructions:\n"
                + normalizeToolPrompt(toolSystemPrompt);
        if (isLlama32Family(configuredModelName)) {
            return promptWithPolicy;
        }
        return promptWithPolicy + "\n\n" + buildAddendum(toolService.toolSpecifications());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when {@code modelName} belongs to the llama3.2
     * family, which has built-in tool-calling support and therefore does not
     * need the XML addendum.
     *
     * @param modelName the configured model name; may be {@code null}.
     */
    private static boolean isLlama32Family(final String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        return modelName.toLowerCase(Locale.ROOT).startsWith("llama3.2");
    }

    private static String buildToolUsePolicy() {
        return """
                Operating rules:
                - Treat tool use as opt-in, not default. Use tools only when they are required to answer a concrete request about the project, codebase, filesystem, or repository.
                - For greetings, pleasantries, acknowledgements, identity questions, and casual chat such as hello, hi, hey, thanks, who are you, or how are you, reply directly in plain language and do not call any tool.
                - Treat current-folder metadata as authoritative context for whether prior work exists on the tagged folder.
                - Interpret previouslyWorkedOnHere=true to mean this exact tagged folder already has cached project knowledge from earlier work.
                - If previouslyWorkedOnHere=true and the task may overlap earlier work, prefer read_project_knowledge before re-investigating.
                - If previouslyWorkedOnHere=false, do not assume cached prior work exists for this folder.
                - Never call change_working_directory unless the user explicitly asks to navigate, switch folders, move into a directory, or provides a specific target path that must be used for the task.
                - Never invent placeholder paths, shell shortcuts, guessed directories, or synthetic values such as ~, ., /home, or example paths when calling tools.
                - If a filesystem request names a vague target such as frontend, webui, config, tests, entry point, or a partial filename, use search_paths first to gather concrete candidates before asking the user to clarify.
                - If search_paths returns exactly one strong candidate, you may use that exact path in follow-up exploration tools. If it returns multiple plausible candidates, summarize them and ask the user to choose before navigating.
                - After completing work that establishes a durable requirement, decision, bug note, or user preference for a project, consider persisting a concise summary with cache_project_knowledge.
                - Use cached knowledge as a supplement to fresh evidence, not a replacement for verifying current code or filesystem state when the task depends on up-to-date facts.
                - If a request is ambiguous and no discovery tool can narrow the target, ask a clarifying question instead of guessing.
                - If you can answer from the conversation alone, answer directly.
                - When not calling a tool, respond with normal prose and never emit JSON, XML, or text that resembles a tool call.
                """;
    }

    private static String normalizeToolPrompt(final String toolSystemPrompt) {
        if (toolSystemPrompt == null || toolSystemPrompt.isBlank()) {
            return "Use tools only when necessary and do not guess missing parameters.";
        }
        return toolSystemPrompt.trim();
    }

    /**
     * Builds the hidden tool-calling instruction that is appended when the
     * model does not support the native tool-calling format.
     *
     * @param specs the list of tool specifications to include.
     * @return the addendum string; never {@code null} or blank.
     */
    private static String buildAddendum(final List<ToolSpecification> specs) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<tool_instructions>\n");
        sb.append("You have access to the following tools. ");
        sb.append("When you need to call a tool, respond ONLY with a <tool_call> block and nothing else. ");
        sb.append("After receiving the tool result, continue your response normally.\n\n");
        sb.append("Available tools:\n");

        for (final ToolSpecification spec : specs) {
            sb.append("- name: ").append(spec.name()).append("\n");
            if (spec.description() != null && !spec.description().isBlank()) {
                sb.append("  description: ").append(spec.description()).append("\n");
            }
            final JsonObjectSchema schema = spec.parameters();
            if (schema != null && schema.properties() != null) {
                sb.append("  parameters:\n");
                schema.properties().forEach((paramName, paramSchema) -> {
                    sb.append("    - ").append(paramName);
                    if (paramSchema instanceof final JsonStringSchema strSchema
                            && strSchema.description() != null) {
                        sb.append(": ").append(strSchema.description());
                    }
                    sb.append("\n");
                });
                if (schema.required() != null && !schema.required().isEmpty()) {
                    sb.append("  required: ").append(String.join(", ", schema.required())).append("\n");
                }
            }
        }

        sb.append("\nTool call format (use this exact XML):\n");
        sb.append("<tool_call>\n");
        sb.append("{\"name\": \"tool_name\", \"arguments\": {\"param\": \"value\"}}\n");
        sb.append("</tool_call>\n");
        sb.append("</tool_instructions>");
        return sb.toString();
    }
}
