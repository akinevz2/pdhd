package ac.uk.sussex.kn253.ollama;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * Builds reusable policy fragments for system prompts.
 */
public final class PromptBuilder {

    public enum PromptRequestType {
        CONVERSATION,
        TOOL_CALL
    }

    public static final String HEADER_ALLOWED_CLAUSES = "Allowed clauses:";
    public static final String ALLOWED_TOOL_EVIDENCE = "- Allowed: Tool calls for requests that require concrete project, codebase, filesystem, or repository evidence.";
    public static final String ALLOWED_DIRECT_CONVERSATION = "- Allowed: Direct plain-language replies for greetings, pleasantries, acknowledgements, identity questions, and casual conversation.";
    public static final String ALLOWED_FOLDER_METADATA_AUTHORITY = "- Allowed: Current-folder metadata as the authoritative context for prior tagged-folder work state.";
    public static final String ALLOWED_HISTORY_SIGNAL = "- Allowed: hasHistory=true as the signal that cached project knowledge exists for the current folder.";
    public static final String ALLOWED_READ_PROJECT_KNOWLEDGE = "- Allowed: read_project_knowledge when hasHistory=true and task overlap with earlier work is likely.";
    public static final String ALLOWED_DISCOVERY_FIRST = "- Allowed: search_paths first when a filesystem target is vague, including names such as frontend, webui, config, tests, entry point, or partial filenames.";
    public static final String ALLOWED_SINGLE_CANDIDATE_FOLLOWUP = "- Allowed: Follow-up exploration with a single strong path candidate returned by search_paths.";
    public static final String ALLOWED_MULTI_CANDIDATE_CLARIFY = "- Allowed: Candidate summaries plus a clarification question when search_paths returns multiple plausible targets.";
    public static final String ALLOWED_DIRECTORY_CHANGE = "- Allowed: change_working_directory after an explicit navigation request with a concrete destination.";
    public static final String ALLOWED_CONCRETE_PATHS = "- Allowed: Paths sourced from user input or tool-discovered candidates.";
    public static final String ALLOWED_CACHE_PROJECT_KNOWLEDGE = "- Allowed: cache_project_knowledge after establishing durable project facts, decisions, constraints, or bug notes.";
    public static final String ALLOWED_CACHE_AS_SUPPLEMENT = "- Allowed: Cached knowledge as a supplement to fresh evidence for tasks that depend on current repository state.";
    public static final String ALLOWED_CLARIFYING_QUESTION = "- Allowed: Clarifying questions when ambiguity remains after discovery.";
    public static final String ALLOWED_DIRECT_ANSWER = "- Allowed: Direct answers from conversation context when sufficient information is already available.";
    public static final String ALLOWED_NORMAL_PROSE = "- Allowed: Normal prose responses whenever no tool call is needed.";

    public static final String HEADER_MAIN_ASSISTANT_INSTRUCTIONS = "Main assistant instructions:";
    public static final String HEADER_TOOL_AGENT_INSTRUCTIONS = "Tool agent instructions:";

    public static final String HEADER_TOOL_CALL_CONTEXT = "Tool-call context:";
    public static final String ALLOWED_TOOL_CALL_RESULT_SYNTHESIS = "- Allowed: Use current tool results and conversation state to produce the next assistant response step.";
    public static final String ALLOWED_TOOL_CALL_STEP_PROGRESS = "- Allowed: Keep progress explicit and concise while preserving request intent.";
    public static final String ALLOWED_TOOL_CALL_NEXT_ACTION = "- Allowed: Use the provided tool agent instructions to determine the next action.";

    public static final String DEFAULT_TOOL_PROMPT = "Use tools only when necessary and provide concrete, evidence-based responses.";

    private PromptBuilder() {
        // utility class
    }

    public static String buildMonolithPrompt(
            final PromptRequestType requestType,
            final String systemPrompt,
            final String toolSystemPrompt,
            final String configuredModelName,
            final List<ToolSpecification> toolSpecifications) {
        if (requestType == PromptRequestType.TOOL_CALL) {
            return buildToolCallPrompt(toolSystemPrompt);
        }
        return buildConversationPrompt(systemPrompt, toolSystemPrompt, configuredModelName, toolSpecifications);
    }

    public static String normalizeToolPrompt(final String toolSystemPrompt) {
        if (toolSystemPrompt == null || toolSystemPrompt.isBlank()) {
            return DEFAULT_TOOL_PROMPT;
        }
        return toolSystemPrompt.trim();
    }

    private static String buildAllowedClausesBlock() {
        return joinWithDoubleBreaks(
                HEADER_ALLOWED_CLAUSES,
                joinWithNewlines(
                        ALLOWED_TOOL_EVIDENCE,
                        ALLOWED_DIRECT_CONVERSATION,
                        ALLOWED_FOLDER_METADATA_AUTHORITY,
                        ALLOWED_HISTORY_SIGNAL,
                        ALLOWED_READ_PROJECT_KNOWLEDGE,
                        ALLOWED_DISCOVERY_FIRST,
                        ALLOWED_SINGLE_CANDIDATE_FOLLOWUP,
                        ALLOWED_MULTI_CANDIDATE_CLARIFY,
                        ALLOWED_DIRECTORY_CHANGE,
                        ALLOWED_CONCRETE_PATHS,
                        ALLOWED_CACHE_PROJECT_KNOWLEDGE,
                        ALLOWED_CACHE_AS_SUPPLEMENT,
                        ALLOWED_CLARIFYING_QUESTION,
                        ALLOWED_DIRECT_ANSWER,
                        ALLOWED_NORMAL_PROSE));
    }

    private static String buildMainAssistantSection(final String systemPrompt) {
        return HEADER_MAIN_ASSISTANT_INSTRUCTIONS + "\n" + systemPrompt;
    }

    private static String buildToolAgentSection(final String toolSystemPrompt) {
        return HEADER_TOOL_AGENT_INSTRUCTIONS + "\n" + normalizeToolPrompt(toolSystemPrompt);
    }

    private static String buildToolCallContextSection() {
        return joinWithDoubleBreaks(
                HEADER_TOOL_CALL_CONTEXT,
                joinWithNewlines(
                        ALLOWED_TOOL_CALL_RESULT_SYNTHESIS,
                        ALLOWED_TOOL_CALL_STEP_PROGRESS,
                        ALLOWED_TOOL_CALL_NEXT_ACTION));
    }

    public static String buildConversationPrompt(
            final String systemPrompt,
            final String toolSystemPrompt,
            final String configuredModelName,
            final List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null || toolSpecifications.isEmpty()) {
            return systemPrompt;
        }
        return joinWithDoubleBreaks(
                buildAllowedClausesBlock(),
                buildMainAssistantSection(systemPrompt),
                buildToolAgentSection(toolSystemPrompt));
    }

    public static String buildToolCallPrompt(final String toolSystemPrompt) {
        return joinWithDoubleBreaks(
                buildToolCallContextSection(),
                buildToolAgentSection(toolSystemPrompt));
    }

    private static String joinWithDoubleBreaks(final String... parts) {
        return String.join("\n\n", parts);
    }

    private static String joinWithNewlines(final String... parts) {
        return String.join("\n", parts);
    }
}
