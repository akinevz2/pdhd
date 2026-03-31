package ac.uk.sussex.kn253.ollama;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * LangChain4j declarative AI service backed by the Ollama model configured in
 * {@code application.properties}.
 *
 * <p>
 * Quarkus wires up the underlying {@link dev.langchain4j.model.chat.ChatModel}
 * automatically via the {@code quarkus-langchain4j-ollama} extension. The model
 * and endpoint are controlled by the standard Quarkus LangChain4j properties:
 *
 * <pre>
 * quarkus.langchain4j.ollama.base-url=http://localhost:11434
 * quarkus.langchain4j.ollama.chat-model.model-id=llama3.1:8b-instruct-q4_K_M
 * </pre>
 *
 * <p>
 * Add new methods here to define additional prompt templates. Each method
 * becomes a distinct "skill" that the AI can perform.
 *
 * <p>
 * Example injection:
 *
 * <pre>{@code
 * @Inject
 * OllamaAiService ai;
 *
 * String answer = ai.chat("What is the capital of France?");
 * String summary = ai.summarise("Long text to summarise …");
 * }</pre>
 */
@RegisterAiService
public interface OllamaAiService {

    // -------------------------------------------------------------------------
    // General chat
    // -------------------------------------------------------------------------

    /**
     * Sends a plain user message and returns the model's reply.
     *
     * @param userMessage the message to send.
     * @return the model's text response.
     */
    @SystemMessage("You are a helpful assistant.")
    String chat(@UserMessage String userMessage);

    // -------------------------------------------------------------------------
    // Summarisation
    // -------------------------------------------------------------------------

    /**
     * Summarises the provided text in a concise paragraph.
     *
     * @param text the text to summarise.
     * @return a concise summary.
     */
    @SystemMessage("You are a precise summarisation assistant. Respond with a single concise paragraph.")
    @UserMessage("Summarise the following text:\n\n{text}")
    String summarise(String text);

    // -------------------------------------------------------------------------
    // Code assistance
    // -------------------------------------------------------------------------

    /**
     * Explains what a piece of code does in plain English.
     *
     * @param code the source code to explain.
     * @return a plain-English explanation.
     */
    @SystemMessage("You are an expert software engineer. Explain code clearly and concisely.")
    @UserMessage("Explain what the following code does:\n\n```\n{code}\n```")
    String explainCode(String code);

    /**
     * Reviews the provided code and suggests improvements.
     *
     * @param code the source code to review.
     * @return a list of improvement suggestions.
     */
    @SystemMessage("""
            You are a senior software engineer performing a code review.
            Identify bugs, style issues, and improvement opportunities.
            Be concise and actionable.
            """)
    @UserMessage("Review the following code:\n\n```\n{code}\n```")
    String reviewCode(String code);

    // -------------------------------------------------------------------------
    // Question answering with context
    // -------------------------------------------------------------------------

    /**
     * Answers a question given a context passage.
     *
     * @param context  the background information.
     * @param question the question to answer.
     * @return the answer derived from the context.
     */
    @SystemMessage("You are a helpful assistant. Answer questions based only on the provided context.")
    @UserMessage("""
            Context:
            {context}

            Question: {question}
            """)
    String answerWithContext(String context, String question);
}
