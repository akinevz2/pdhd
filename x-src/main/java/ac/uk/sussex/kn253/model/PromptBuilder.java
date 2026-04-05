package ac.uk.sussex.kn253.model;

/**
 * Builds reusable policy fragments for system prompts.
 */
public final class PromptBuilder {

    public enum PromptRequestType {
        CONVERSATION,
        TOOL_CALL
    }

    public static final String HEADER_MAIN_ASSISTANT_INSTRUCTIONS = "Main assistant instructions:";
    public static final String HEADER_TOOL_AGENT_INSTRUCTIONS = "Tool agent instructions:";

    public static final String HEADER_TOOL_CALL_CONTEXT = "Tool-call context:";
    public static final String TOOLCALL_MARKUP_SQUARE = "[toolcall prompt=\"<next prompt for the user to send back>\" tool=\"<tool name>\"]<button label>[/toolcall]";
    public static final String REQUIRED_TOOLCALL_CONFIRMATION_MARKUP_TEMPLATE = "- Required: If another tool call is needed after the first tool result for the current user request, do not issue that tool call directly. Instead reply with markdown containing exactly %s.";
    public static final String REQUIRED_TOOLCALL_CONFIRMATION_WAIT_TEMPLATE = "- Required: After emitting a %s block, stop and wait for user confirmation rather than making another tool call.";
    public static final String TOOLCALL_BLOCK_EXAMPLE = "toolcall confirmation";

    public static final String REQUIRED_TOOL_CALL_RESULT_SYNTHESIS = "- Required: Use current tool results and conversation state to produce the next assistant response step.";
    public static final String REQUIRED_TOOL_CALL_STEP_PROGRESS = "- Required: Keep progress explicit and concise while preserving request intent.";
    public static final String REQUIRED_TOOL_CALL_NEXT_ACTION = "- Required: Use the provided tool agent instructions to determine the next action.";
    public static final String REQUIRED_SINGLE_DIRECT_TOOL_CALL = "- Required: Issue one direct tool call when concrete evidence is needed and no tool has yet been used for the current user request.";
    public static final String REQUIRED_TOOLCALL_CONFIRMATION_MARKUP_SQUARE = String.format(
            REQUIRED_TOOLCALL_CONFIRMATION_MARKUP_TEMPLATE,
            TOOLCALL_MARKUP_SQUARE);
    public static final String REQUIRED_TOOLCALL_CONFIRMATION_MARKUP = REQUIRED_TOOLCALL_CONFIRMATION_MARKUP_SQUARE;
    public static final String REQUIRED_TOOLCALL_CONFIRMATION_WAIT = String.format(
            REQUIRED_TOOLCALL_CONFIRMATION_WAIT_TEMPLATE,
            TOOLCALL_BLOCK_EXAMPLE);

    private PromptBuilder() {
        // utility class
    }

    public static String buildPrompt(final String... systemPrompt) {
        return joinWithDoubleBreaks(systemPrompt);
    }

    private static String joinWithDoubleBreaks(final String... parts) {
        return String.join("\n\n", parts);
    }

    @SuppressWarnings("unused")
    private static String joinWithNewlines(final String... parts) {
        return String.join("\n", parts);
    }
}
