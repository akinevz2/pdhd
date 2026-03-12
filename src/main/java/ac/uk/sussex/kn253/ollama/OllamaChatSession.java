package ac.uk.sussex.kn253.ollama;

import java.time.Duration;
import java.util.*;

import org.jboss.logging.Logger;

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

    private final ChatModel chatModel;
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
    }

    /**
     * Programmatic constructor that accepts a pre-built {@link ChatModel}.
     * Useful for testing with mocks or alternative model implementations.
     *
     * @param chatModel the model to use for inference.
     */
    public OllamaChatSession(final ChatModel chatModel) {
        this.chatModel = chatModel;
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

        final List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(history);

        LOG.debugf("Sending %d messages to Ollama (system + %d history turns)",
                messages.size(), history.size());

        final ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .build();

        final ChatResponse response = chatModel.chat(request);
        final String replyText = response.aiMessage().text();

        history.add(AiMessage.from(replyText));
        return replyText;
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
        final ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userText))
                .build();

        final ChatResponse response = chatModel.chat(request);
        return response.aiMessage().text();
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

    public static OllamaChatSessionBuilder builder() {
        return new OllamaChatSessionBuilder();
    }
}
