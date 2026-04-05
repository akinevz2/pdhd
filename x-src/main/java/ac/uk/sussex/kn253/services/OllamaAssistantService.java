package ac.uk.sussex.kn253.services;

import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import ac.uk.sussex.kn253.model.ollama.OllamaConfig;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class OllamaAssistantService {

    private static final Logger LOG = Logger
            .getLogger(OllamaAssistantService.class.getName());

    @Inject
    IChatService chatService;

    @Inject
    ModelConfigService configService;

    @Inject
    OllamaConfig ollamaConfig;

    private OllamaContainer ollama;

    @PreDestroy
    void onDestroy() {
        if (ollama != null && ollama.isRunning()) {
            LOG.info("Stopping Ollama container...");
            ollama.stop();
        }
    }

    @PostConstruct
    void init() {
        if (!ollamaConfig.enabled())
            return;
        if (!ollamaConfig.baseUrl().isBlank()) {
            LOG.info("Using external Ollama instance at " + ollamaConfig.baseUrl());
            LOG.fine("Delete baseUrl key to enable Ollama testcontainers");
            return;
        }
        LOG.info("Starting Ollama Container...");
        var ollamaImage = ollamaConfig.ollamaImage();
        DockerImageName imageName = DockerImageName.parse(ollamaImage);
        ollama = new OllamaContainer(imageName);
        ollama.start();
    }

    public String chat(final String input) {
        return chatService.streamResponse(input)
                .collect()
                .asList()
                .await()
                .indefinitely()
                .stream()
                .collect(Collectors.joining());
    }

    public Multi<String> streamResponse(final String input) {
        return chatService.streamResponse(input)
                .onFailure()
                .invoke(t -> {
                    // Log the error but don't propagate it, to avoid breaking the stream
                    LOG.severe("Error streaming response: " + t.getMessage());
                })
                .onFailure()
                .recoverWithMulti(Multi.createFrom().item(() -> ""));
    }

    public StreamingChatModel getModel() {
        return chatService.model();
    }

    public EmbeddingModel getEmbeddingModel() {
        return chatService.embeddingModel();
    }

}
