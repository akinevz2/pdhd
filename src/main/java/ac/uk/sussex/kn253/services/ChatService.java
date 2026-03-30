package ac.uk.sussex.kn253.services;

import java.util.Locale;

import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.Main;
import ac.uk.sussex.kn253.model.EmbeddingVector;
import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.ollama.OllamaChatSession;
import ac.uk.sussex.kn253.schema.ToolSupport;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application-scoped service that owns the active
 * {@link ac.uk.sussex.kn253.ollama.OllamaChatSession}.
 *
 * <p>
 * On startup the session is initialised from the persisted
 * {@link OllamaSettings}. Call {@link #reconfigure(OllamaSettings)} whenever
 * settings change (e.g. after the user saves the Ollama config menu) to swap
 * in a fresh session without restarting the application.
 */
@ApplicationScoped
public class ChatService {

    private static final Logger LOG = Logger.getLogger(ChatService.class);
    private static final String EMBEDDINGS_SESSION_ID = "default-chat-session";

    @Inject
    ToolService toolService;

    @Inject
    ToolActivityService toolActivityService;

    @Inject
    OllamaConfigService ollamaConfigService;

    @Inject
    Main main;

    @Inject
    WorkingDirectoryService workingDirectoryService;

    @Inject
    CurrentFolderMetadataService currentFolderMetadataService;

    @Inject
    EmbeddingService embeddingService;

    private OllamaChatSession chatSession;

    @PostConstruct
    void init() {
        reconfigure(ollamaConfigService.load());
    }

    /**
     * Rebuilds the underlying chat session from the given settings.
     * Called on startup and after the user saves new config.
     */
    public void reconfigure(final OllamaSettings settings) {
        final String systemPrompt = withDefaultPrompt(
                settings.getSystemPrompt(),
                OllamaSettings.DEFAULT_SYSTEM_PROMPT);
        final String toolSystemPrompt = withDefaultPrompt(
                settings.getToolSystemPrompt(),
                OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT);

        this.chatSession = OllamaChatSession.builder()
                .baseUrl(settings.getBaseUrl())
                .model(settings.getModelName())
                .timeoutSeconds(settings.getTimeoutSeconds())
                .temperature(settings.getTemperature())
                .numPredict(settings.getNumPredict())
                .numCtx(settings.getNumCtx())
                .toolService(toolService)
                .toolActivityService(toolActivityService)
                .build()
                .setSystemPrompt(systemPrompt)
                .setToolSystemPrompt(toolSystemPrompt)
                .setRequestMetadataSupplier(() -> currentFolderMetadataService.buildPromptContext());
    }

    public String sendMessage(final String message) {
        final String directReply = directReply(message);
        if (directReply != null) {
            return directReply;
        }

        if (!embeddingService.isEnabled()) {
            return chatSession.send(message);
        }

        try {
            final EmbeddingVector embedding = embeddingService.generateEmbedding(
                    message,
                    EMBEDDINGS_SESSION_ID);
            embeddingService.storeEmbedding(
                    embedding,
                    "user-input-" + System.currentTimeMillis(),
                    ToolSupport.VALUE_USER_INPUT,
                    EMBEDDINGS_SESSION_ID);
        } catch (final Exception e) {
            LOG.errorf(e, "Failed to generate/store embedding for chat message");
            throw new IllegalStateException("Failed to generate embeddings for this message", e);
        }

        return chatSession.send(message);
    }

    public String sendOneShotMessage(final String message) {
        final String directReply = directReply(message);
        if (directReply != null) {
            return directReply;
        }

        if (!embeddingService.isEnabled()) {
            return chatSession.sendOneShot(message);
        }

        try {
            final EmbeddingVector embedding = embeddingService.generateEmbedding(
                    message,
                    EMBEDDINGS_SESSION_ID);
            embeddingService.storeEmbedding(
                    embedding,
                    "user-input-oneshot-" + System.currentTimeMillis(),
                    ToolSupport.VALUE_USER_INPUT,
                    EMBEDDINGS_SESSION_ID);
        } catch (final Exception e) {
            LOG.errorf(e, "Failed to generate/store embedding for one-shot message");
            throw new IllegalStateException("Failed to generate embeddings for this message", e);
        }

        return chatSession.sendOneShot(message);
    }

    public void resetConversation() {
        if (chatSession != null) {
            chatSession.reset();
        }
    }

    private String directReply(final String message) {
        if (message == null) {
            return null;
        }

        final String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }

        return switch (normalized) {
            case "hello", "hi", "hey", "hello there", "hi there", "hey there", "/hi" ->
                "Hello. Ask about the project, files, or repository and I can inspect them.";
            case "thanks", "thank you", "cheers", "/thanks" ->
                "You're welcome.";
            case "goodbye", "bye", "see you", "see ya", "/bye" -> {
                main.exit();
                yield "Goodbye.";
            }

            default -> null;
        };
    }

    private String withDefaultPrompt(final String configuredPrompt, final String defaultPrompt) {
        if (configuredPrompt == null || configuredPrompt.isBlank()) {
            return defaultPrompt;
        }
        return configuredPrompt;
    }
}
