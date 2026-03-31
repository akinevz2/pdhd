package ac.uk.sussex.kn253.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.Main;
import ac.uk.sussex.kn253.model.EmbeddingVector;
import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.ollama.OllamaChatSession;
import ac.uk.sussex.kn253.ollama.MacroContext;
import ac.uk.sussex.kn253.schema.ToolSupport;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacros;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
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
    EmbeddingService embeddingService;

    private OllamaChatSession chatSession;

    ChatService() {
    }

    /**
     * Package-private constructor for unit tests — only sets the dependencies
     * needed for directory logic.
     */
    ChatService(final WorkingDirectoryService workingDirectoryService) {
        this.workingDirectoryService = workingDirectoryService;
    }

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

            final MacroContext macroContext = new MacroContext(workingDirectoryService);

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
                .setContextSupplier(() -> macroContext);
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

    public String summarizeDirectory(final String rawPath) {
        final Path directory = resolveExistingDirectory(rawPath);
        final boolean projectRoot = ProjectRootSupport.isProjectRootDirectory(directory);
        final String toolName = selectManifestTool(projectRoot);

        final String toolArgs = "{\"path\":\"" + escapeJson(directory.toString()) + "\"}";
        final String evidence = toolService.execute(
                ToolExecutionRequest.builder()
                        .name(toolName)
                        .arguments(toolArgs)
                        .build(),
                null);

        if (evidence == null || evidence.isBlank()) {
            throw new IllegalStateException("No evidence returned for folder summary");
        }

        return sendOneShotMessage(buildSummaryPrompt(projectRoot, compactEvidence(evidence)));
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

    private Path resolveExistingDirectory(final String rawPath) {
        final String trimmed = rawPath == null ? "" : rawPath.trim();
        final Path base = workingDirectoryService.getCurrentWorkingDirectory();
        final Path candidate = trimmed.isBlank()
                ? base
                : (Path.of(trimmed).isAbsolute() ? Path.of(trimmed) : base.resolve(trimmed));
        final Path normalized = candidate.normalize().toAbsolutePath();

        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Not a directory: " + rawPath);
        }
        return normalized;
    }

    /**
     * Compacts raw manifest evidence by trimming trailing whitespace from every
     * line and collapsing consecutive blank lines into a single blank line.
     * This reduces token count without discarding any path or content data.
     * Visible for testing.
     */
    String compactEvidence(final String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        final String[] lines = raw.split("\n", -1);
        final StringBuilder sb = new StringBuilder(raw.length());
        boolean lastWasBlank = false;
        for (final String line : lines) {
            final String trimmed = line.stripTrailing();
            final boolean blank = trimmed.isEmpty();
            if (blank && lastWasBlank) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
            lastWasBlank = blank;
        }
        return sb.toString();
    }

    /** Visible for testing. */
    String selectManifestTool(final boolean projectRoot) {
        return projectRoot ? ToolMacros.READ_PROJECT_MANIFEST.name() : ToolMacros.READ_FOLDER_MANIFEST.name();
    }

    /** Visible for testing. */
    String buildSummaryPrompt(final boolean projectRoot, final String evidence) {
        // FIXME: string based instrumentation
        final String actionHint = projectRoot
                ? "End with one assistant-action block whose prompt continues exploration of a key subfolder."
                : "End with one assistant-action block whose prompt continues exploration of a relevant child folder.";
        return String.join("\n",
                "Summarise this folder for a developer.",
                "",
                actionHint,
                "",
                "Evidence:",
                evidence);
    }

    /** Visible for testing. */
    private String escapeJson(final String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
