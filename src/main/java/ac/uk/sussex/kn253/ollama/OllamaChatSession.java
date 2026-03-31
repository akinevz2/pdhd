package ac.uk.sussex.kn253.ollama;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.services.ToolActivityService;
import ac.uk.sussex.kn253.services.ToolService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

/**
 * A stateful, multi-turn chat session backed by an Ollama model.
 *
 * <p>
 * Each instance maintains its own conversation history so that the model
 * receives full context on every turn. Call {@link #reset()} to start a fresh
 * conversation without creating a new bean.
 *
 * <p>
 * This bean is {@link Dependent}-scoped so that each injection point
 * receives its own independent session. Change the scope to
 * {@link jakarta.enterprise.context.ApplicationScoped} if a shared session is
 * required.
 *
 * <p>
 * System-prompt construction is delegated to {@link PromptBuilder}.
 *
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * @Inject
 * OllamaChatSession session;
 *
 * session.setSystemPrompt("You are a helpful coding assistant.");
 * String reply1 = session.send("What is a monad?");
 * String reply2 = session.send("Can you give me a Java example?");
 * }</pre>
 */
@Dependent
public class OllamaChatSession {

    private static final Logger LOG = Logger.getLogger(OllamaChatSession.class);
    private static final int MAX_TOOL_ROUNDS = 8;
    private static final int MAX_IDENTICAL_TOOL_CALLS_DEFAULT = 4;
    private static final int MAX_IDENTICAL_TOOL_CALLS_SUMMARIZE = 3;
    private static final int MAX_IDENTICAL_TOOL_CALLS_EXPLORATION = 7;
    private final ChatModel chatModel;
    private final String configuredModelName;
    private final ToolService toolService;
    private final ToolActivityService toolActivityService;
    private final List<ChatMessage> history = new ArrayList<>();
    private String systemPrompt = "You are a helpful assistant.";
    private String toolSystemPrompt = "Use tools only when necessary.";
    private Supplier<MacroContext> contextSupplier = null;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * CDI constructor – the model is built from the injected {@link OllamaConfig}.
     *
     * @param config Ollama model configuration.
     */
    @Inject
    public OllamaChatSession(final OllamaConfig config) {
        this.chatModel = buildModel(config.baseUrl(), config.modelName(),
                config.temperature(), config.numPredict(), config.numCtx(), config.timeoutSeconds());
        this.configuredModelName = config.modelName();
        this.toolService = null;
        this.toolActivityService = null;
    }

    /**
     * Programmatic constructor used by {@link OllamaChatSessionBuilder}.
     *
     * @param builder the fully configured builder.
     */
    public OllamaChatSession(final OllamaChatSessionBuilder builder) {
        this.chatModel = buildModel(builder.baseUrl(), builder.modelName(),
                builder.temperature(), builder.numPredict(), builder.numCtx(), builder.timeoutSeconds());
        this.configuredModelName = builder.modelName();
        this.toolService = builder.toolService();
        this.toolActivityService = builder.toolActivityService();
    }

    /**
     * Programmatic constructor for use outside CDI (e.g. tests or scripts).
     *
     * @param baseUrl   Ollama base URL (e.g. {@code http://localhost:11434}).
     * @param modelName model identifier (e.g. {@code llama3.1:8b-instruct-q4_K_M}).
     */
    public OllamaChatSession(final String baseUrl, final String modelName) {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
        this.configuredModelName = modelName;
        this.toolService = null;
        this.toolActivityService = null;
    }

    /**
     * Programmatic constructor that accepts a pre-built {@link ChatModel}.
     * Useful for testing with mocks or alternative model implementations.
     *
     * @param chatModel the model to use for inference.
     */
    public OllamaChatSession(final ChatModel chatModel) {
        this.chatModel = chatModel;
        this.configuredModelName = null;
        this.toolService = null;
        this.toolActivityService = null;
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /**
     * Sets the system prompt prepended to every request.
     * Calling this method does <em>not</em> reset the conversation history.
     *
     * @param systemPrompt the system instruction for the model.
     * @return {@code this} for fluent chaining.
     */
    public OllamaChatSession setSystemPrompt(final String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    /**
     * Sets additional instructions used only for tool-enabled conversations.
     *
     * @param toolSystemPrompt system instruction for tool-using flows.
     * @return {@code this} for fluent chaining.
     */
    public OllamaChatSession setToolSystemPrompt(final String toolSystemPrompt) {
        this.toolSystemPrompt = toolSystemPrompt;
        return this;
    }

    /**
     * Sets a supplier that is called on every conversation turn to provide
     * macro prompt context.
     */
    public OllamaChatSession setContextSupplier(final Supplier<MacroContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
        return this;
    }

    /**
     * Compatibility wrapper while callers migrate to
     * {@link #setContextSupplier(Supplier)}.
     */
    public OllamaChatSession setCwdSupplier(final Supplier<Path> cwdSupplier) {
        return setContextSupplier(() -> new MacroContext(cwdSupplier.get()));
    }

    // -------------------------------------------------------------------------
    // Chat API
    // -------------------------------------------------------------------------

    /**
     * Sends a user message and returns the model reply, maintaining full
     * conversation history across calls.
     *
     * @param userText the user's message.
     * @return the assistant's text response.
     */
    public String send(final String userText) {
        initializeConversation(history);
        history.add(UserMessage.from(userText));
        return runConversationLoop(true, history);
    }

    /**
     * Sends a one-shot message <em>without</em> persisting it to the
     * conversation history. Useful for quick queries that should not affect
     * the ongoing dialogue.
     *
     * @param userText the user's message.
     * @return the assistant's text response.
     */
    public String sendOneShot(final String userText) {
        final List<ChatMessage> oneShot = new ArrayList<>();
        initializeConversation(oneShot);
        oneShot.add(UserMessage.from(userText));
        return runConversationLoop(false, oneShot);
    }

    // -------------------------------------------------------------------------
    // History management
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of the current conversation history.
     * The system message is not included.
     */
    public List<ChatMessage> getHistory() {
        return history.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .toList();
    }

    /**
     * Clears the conversation history, effectively starting a new session.
     * The system prompt is preserved.
     *
     * @return {@code this} for fluent chaining.
     */
    public OllamaChatSession reset() {
        history.clear();
        LOG.debug("Chat session history cleared.");
        return this;
    }

    /**
     * Returns the number of complete turns (user + assistant message pairs)
     * currently in the history.
     */
    public int turnCount() {
        return getHistory().size() / 2;
    }

    // -------------------------------------------------------------------------
    // Conversation loop
    // -------------------------------------------------------------------------

    /**
     * Drives the model interaction loop, executing tool calls until the model
     * returns a plain text response or the maximum round limit is reached.
     *
     * @param persistentHistory ignored; retained for signature symmetry.
     * @param workingHistory    the list of messages to send; mutated in place.
     * @return the final assistant text response.
     */
    private String runConversationLoop(
            final boolean persistentHistory,
            final List<ChatMessage> workingHistory) {
        String lastToolSignature = null;
        int identicalToolCallCount = 0;

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            if (toolService == null) {
                LOG.warn("Running conversation loop with no tools accessible to the model");
            }
            final List<dev.langchain4j.agent.tool.ToolSpecification> specs = toolService == null ? List.of()
                    : toolService.toolSpecifications();

            LOG.debugf("Sending %d messages to Ollama", workingHistory.size());

            ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(workingHistory);
            if (!specs.isEmpty()) {
                requestBuilder = requestBuilder.toolSpecifications(specs);
            }

            final ChatResponse response = chatModel.chat(requestBuilder.build());
            final AiMessage aiMessage = response.aiMessage();
            workingHistory.add(aiMessage);

            if (aiMessage.hasToolExecutionRequests()) {
                final List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();

                LOG.debugf("Model requested %d tool calls", toolRequests.size());

                for (final ToolExecutionRequest toolRequest : toolRequests) {
                    final String signature = toolRequest.name() + "|" + toolRequest.arguments();
                    if (signature.equals(lastToolSignature)) {
                        identicalToolCallCount++;
                    } else {
                        lastToolSignature = signature;
                        identicalToolCallCount = 1;
                    }

                    if (identicalToolCallCount >= maxIdenticalToolCalls(toolRequest.name())) {
                        final String loopGuardResult = "Tool loop guard triggered: repeated identical tool call ('"
                                + toolRequest.name()
                                + "'). Synthesize a final response from the current evidence instead of re-calling this tool.";
                        if (toolActivityService != null) {
                            toolActivityService.record(toolRequest.name(), toolRequest.arguments(), loopGuardResult);
                        }
                        workingHistory.add(ToolExecutionResultMessage.from(toolRequest, loopGuardResult));
                        LOG.warnf("Stopped repeated identical tool call loop for tool '%s'", toolRequest.name());
                        return "Stopped a repeated tool-call loop ('" + toolRequest.name()
                                + "'). Please retry with a narrower request or explicit target path.";
                    }

                    final String result = toolService.execute(toolRequest, null);
                    if (toolActivityService != null) {
                        toolActivityService.record(toolRequest.name(), toolRequest.arguments(), result);
                    }
                    workingHistory.add(ToolExecutionResultMessage.from(toolRequest, result));
                }

                // Tool results are now part of the conversation history; the next
                // outer round asks the model for a fresh assistant message.
                continue;
            }

            final String text = aiMessage.text();
            if (text == null || text.isBlank()) {
                return "Assistant is thinking...";
            }

            return text;

        }

        return "Tool execution exceeded maximum rounds without a final response.";
    }

    private void initializeConversation(final List<ChatMessage> workingHistory) {
        if (!workingHistory.isEmpty()) {
            return;
        }

        final String effectiveSystemPrompt = buildEffectiveSystemPrompt();
        final List<dev.langchain4j.agent.tool.ToolSpecification> specs = toolService == null ? List.of()
                : toolService.toolSpecifications();
        workingHistory.add(SystemMessage.from(
                PromptBuilder.buildMonolithPrompt(
                        PromptBuilder.PromptRequestType.CONVERSATION,
                        effectiveSystemPrompt,
                        toolSystemPrompt,
                        configuredModelName,
                        specs)));
    }

    String buildEffectiveSystemPrompt() {

        if (contextSupplier == null) {
            return systemPrompt;
        }

        final MacroContext context = contextSupplier.get();
        if (context == null) {
            return systemPrompt;
        }

        final Path cwd = context.cwd();
        if (cwd == null) {
            return systemPrompt;
        }

        final String cwdText = cwd.toString();
        if (cwdText.isBlank()) {
            return systemPrompt;
        }

        return systemPrompt + "\n\nCurrent working directory: " + cwdText;
    }

    private static int maxIdenticalToolCalls(final String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return MAX_IDENTICAL_TOOL_CALLS_DEFAULT;
        }
        return switch (toolName) {
            // Keep summarize loops strict; these were the problematic loops.
            case "summarize_path" -> MAX_IDENTICAL_TOOL_CALLS_SUMMARIZE;

            // Exploration workflows may legitimately repeat while traversing.
            case "list_subdirectories",
                    "list_files_recursive",
                    "list_project_entries",
                    "get_git_log",
                    "get_path_info",
                    "resolve_path",
                    "get_current_working_directory" ->
                MAX_IDENTICAL_TOOL_CALLS_EXPLORATION;

            default -> MAX_IDENTICAL_TOOL_CALLS_DEFAULT;
        };
    }

    // -------------------------------------------------------------------------
    // Builder factory
    // -------------------------------------------------------------------------

    /** Returns a new {@link OllamaChatSessionBuilder}. */
    public static OllamaChatSessionBuilder builder() {
        return new OllamaChatSessionBuilder();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds an {@link OllamaChatModel} using the supplied configuration values.
     * Optional parameters (numPredict, numCtx) are omitted when {@code <= 0}.
     */
    private static ChatModel buildModel(
            final String baseUrl,
            final String modelName,
            final Double temperature,
            final int numPredict,
            final int numCtx,
            final long timeoutSeconds) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .numPredict(numPredict > 0 ? numPredict : null)
                .numCtx(numCtx > 0 ? numCtx : null)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }
}
