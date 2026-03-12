package ac.uk.sussex.kn253.services;

import ac.uk.sussex.kn253.ollama.OllamaChatSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ChatService {

    private static final String BASE_URL = "http://VISION-WS:11434";
    private static final String MODEL_NAME = "qwen3-coder:30b";
    OllamaChatSession chatSession;

    @Inject
    ToolService toolService;

    public ChatService() {
        super();
        final var env = System.getenv();
        final String baseUrl = env.getOrDefault("OLLAMA_ENDPOINT", BASE_URL);
        final String modelName = env.getOrDefault("OLLAMA_MODEL", MODEL_NAME);
        final OllamaChatSession session = OllamaChatSession.builder()
                .baseUrl(baseUrl)
                .model(modelName)
                .toolService(toolService)
                .build();
        this.chatSession = session;
    }

    public String sendMessage(final String message) {

        return chatSession.send(message);

    }

}
