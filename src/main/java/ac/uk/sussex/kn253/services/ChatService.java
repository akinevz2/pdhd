package ac.uk.sussex.kn253.services;

import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.ollama.OllamaChatSession;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ChatService {

    @Inject
    ToolService toolService;

    @Inject
    ToolActivityService toolActivityService;

    @Inject
    OllamaConfigService ollamaConfigService;

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
        final String systemPrompt = settings.getSystemPrompt() == null || settings.getSystemPrompt().isBlank()
                ? OllamaSettings.DEFAULT_SYSTEM_PROMPT
                : settings.getSystemPrompt();

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
                .setSystemPrompt(systemPrompt);
    }

    public String sendMessage(final String message) {
        return chatSession.send(message);
    }
}
