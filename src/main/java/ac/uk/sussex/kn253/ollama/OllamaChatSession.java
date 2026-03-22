package ac.uk.sussex.kn253.ollama;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.services.ToolActivityService;
import ac.uk.sussex.kn253.services.ToolService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

/**
 * A stateful, multi-turn chat session backed by an Ollama model.
 *
 * <p>
 * Each instance maintains its own conversation history so that the model
 * receives the full context on every turn. Use {@link #reset()} to start a
 * fresh conversation without creating a new bean.
 *
 * <p>
 * This bean is {@link Dependent}-scoped so that each injection point gets its
 * own independent session. If you need a shared session, change the scope to
 * {@link jakarta.enterprise.context.ApplicationScoped} or manage the lifecycle
 * yourself.
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
    private static final ObjectMapper TOOL_MAPPER = new ObjectMapper();
    // Matches tool calls wrapped in <tool_call>…</tool_call> or ```json…``` fences
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "<tool_call>\\s*(\\{[\\s\\S]*?\\})\\s*</tool_call>" +
                    "|```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```",
            Pattern.CASE_INSENSITIVE);

    private final ChatModel chatModel;
    private final ToolService toolService;
    private final ToolActivityService toolActivityService;
    private final List<ChatMessage> history = new ArrayList<>();
    private String systemPrompt = "You are a helpful assistant.";

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * CDI constructor – the model is built from {@link OllamaConfig}.
     */
    @Inject
    public OllamaChatSession(final OllamaConfig config) {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
                .temperature(config.temperature())
                .numPredict(config.numPredict() > 0 ? config.numPredict() : null)
                .numCtx(config.numCtx() > 0 ? config.numCtx() : null)
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .build();
        this.toolService = null;
        this.toolActivityService = null;
    }

    public OllamaChatSession(final OllamaChatSessionBuilder builder) {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(builder.baseUrl())
                .modelName(builder.modelName())
                .temperature(builder.temperature())
                .numPredict(builder.numPredict() > 0 ? builder.numPredict() : null)
                .numCtx(builder.numCtx() > 0 ? builder.numCtx() : null)
                .timeout(Duration.ofSeconds(builder.timeoutSeconds()))
                .build();
        this.toolService = builder.toolService();
        this.toolActivityService = builder.toolActivityService();
    }

    /**
     * Programmatic constructor for use outside CDI (e.g. tests or scripts).
     *
     * @param baseUrl   Ollama base URL, e.g. {@code http://localhost:11434}.
     * @param modelName model to use, e.g. {@code llama3.2}.
     */
    public OllamaChatSession(final String baseUrl, final String modelName) {
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
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
        this.toolService = null;
        this.toolActivityService = null;
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /**
     * Sets the system prompt that will be prepended to every request.
     * Calling this method does <em>not</em> reset the conversation history.
     *
     * @param systemPrompt the system instruction for the model.
     * @return {@code this} for fluent chaining.
     */
    public OllamaChatSession setSystemPrompt(final String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    // -------------------------------------------------------------------------
    // Chat
    // -------------------------------------------------------------------------

    /**
     * Sends a user message and returns the model's reply, maintaining full
     * conversation history across calls.
     *
     * @param userText the user's message.
     * @return the model's text response.
     */
    public String send(final String userText) {
        history.add(UserMessage.from(userText));
        return runConversationLoop(true);
    }

    /**
     * Sends a one-shot message <em>without</em> adding it to the conversation
     * history. Useful for quick queries that should not affect the ongoing
     * dialogue.
     *
     * @param userText the user's message.
     * @return the model's text response.
     */
    public String sendOneShot(final String userText) {
        final List<ChatMessage> oneShot = new ArrayList<>();
        oneShot.add(UserMessage.from(userText));
        return runConversationLoop(false, oneShot);
    }

    // -------------------------------------------------------------------------
    // History management
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of the current conversation history.
     * The list does <em>not</em> include the system message.
     */
    public List<ChatMessage> getHistory() {
        return Collections.unmodifiableList(history);
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
     * Returns the number of turns (user + assistant messages) in the history.
     */
    public int turnCount() {
        // Each turn = 1 user message + 1 assistant message = 2 entries
        return history.size() / 2;
    }

    private String runConversationLoop(final boolean persistentHistory) {
        return runConversationLoop(persistentHistory, history);
    }

    private String runConversationLoop(final boolean persistentHistory, final List<ChatMessage> workingHistory) {
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            final List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(buildSystemPrompt()));
            messages.addAll(workingHistory);

            LOG.debugf("Sending %d messages to Ollama (system + %d history entries)",
                    messages.size(), workingHistory.size());

            ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(messages);
            if (toolService != null && !toolService.toolSpecifications().isEmpty()) {
                requestBuilder = requestBuilder.toolSpecifications(toolService.toolSpecifications());
            }

            final ChatResponse response = chatModel.chat(requestBuilder.build());
            final AiMessage aiMessage = response.aiMessage();
            workingHistory.add(aiMessage);

            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();

            // Fallback: some models print tool calls as JSON text rather than using
            // the structured tool-call response format – detect and parse them.
            if ((toolRequests == null || toolRequests.isEmpty()) && toolService != null) {
                toolRequests = parseTextToolCalls(aiMessage.text());
            }

            if (toolRequests == null || toolRequests.isEmpty()) {
                return aiMessage.text();
            }

            for (final ToolExecutionRequest toolRequest : toolRequests) {
                final String result = toolService.execute(toolRequest, null);
                if (toolActivityService != null) {
                    toolActivityService.record(toolRequest.name(), toolRequest.arguments(), result);
                }
                workingHistory.add(ToolExecutionResultMessage.from(toolRequest, result));
            }
        }

        return "Tool execution exceeded maximum rounds without a final response.";
    }

    // -------------------------------------------------------------------------
    // System-prompt construction
    // -------------------------------------------------------------------------

    /**
     * Returns the effective system prompt: the user-configured prompt plus a
     * hidden addendum that instructs models without native tool-calling support
     * (e.g. qwen2.5-coder) to emit tool invocations as XML.
     */
    private String buildSystemPrompt() {
        if (toolService == null || toolService.toolSpecifications().isEmpty()) {
            return systemPrompt;
        }
        return systemPrompt + "\n\n" + buildToolCallingAddendum(toolService.toolSpecifications());
    }

    private static String buildToolCallingAddendum(final List<ToolSpecification> specs) {
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
            if (spec.parameters() instanceof final JsonObjectSchema schema && schema.properties() != null) {
                sb.append("  parameters:\n");
                schema.properties().forEach((paramName, paramSchema) -> {
                    sb.append("    - ").append(paramName);
                    if (paramSchema instanceof final JsonStringSchema strSchema && strSchema.description() != null) {
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

    // -------------------------------------------------------------------------
    // Text tool-call fallback parser
    // -------------------------------------------------------------------------

    private List<ToolExecutionRequest> parseTextToolCalls(final String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        final List<ToolExecutionRequest> results = new ArrayList<>();

        // Try tagged / fenced blocks first
        final Matcher m = TOOL_CALL_PATTERN.matcher(text);
        while (m.find()) {
            final String json = m.group(1) != null ? m.group(1) : m.group(2);
            parseOneToolCall(json).ifPresent(results::add);
        }
        if (!results.isEmpty()) {
            return results;
        }

        // Fall back to treating the entire trimmed text as a bare JSON object
        final String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            parseOneToolCall(trimmed).ifPresent(results::add);
        }
        return results;
    }

    private Optional<ToolExecutionRequest> parseOneToolCall(final String json) {
        try {
            final JsonNode node = TOOL_MAPPER.readTree(json);
            String name = null;
            String arguments = "{}";

            if (node.has("name")) {
                name = node.get("name").asText();
                final JsonNode argsNode = node.has("arguments") ? node.get("arguments")
                        : node.has("parameters") ? node.get("parameters") : null;
                if (argsNode != null) {
                    arguments = argsNode.isObject() ? TOOL_MAPPER.writeValueAsString(argsNode) : argsNode.asText();
                }
            } else if (node.has("function")) {
                final JsonNode fn = node.get("function");
                name = fn.has("name") ? fn.get("name").asText() : null;
                final JsonNode argsNode = fn.has("arguments") ? fn.get("arguments") : null;
                if (argsNode != null) {
                    arguments = argsNode.isObject() ? TOOL_MAPPER.writeValueAsString(argsNode) : argsNode.asText();
                }
            }

            final String resolvedName = name;
            if (resolvedName != null
                    && toolService.toolSpecifications().stream().anyMatch(s -> s.name().equals(resolvedName))) {
                return Optional.of(ToolExecutionRequest.builder().name(name).arguments(arguments).build());
            }
            LOG.debugf("Text contained JSON but no matching tool found for name: %s", name);
        } catch (final Exception e) {
            LOG.debugf("Could not parse text as tool call: %s", e.getMessage());
        }
        return Optional.empty();
    }

    public static OllamaChatSessionBuilder builder() {
        return new OllamaChatSessionBuilder();
    }
}
