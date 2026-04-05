package ac.uk.sussex.kn253.ollama;

import ac.uk.sussex.kn253.services.IChatService;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.test.Mock;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@Mock
@ApplicationScoped
public class TestChatService implements IChatService {

    @Override
    public StreamingChatModel model() {
        return null;
    }

    @Override
    public EmbeddingModel embeddingModel() {
        return null;
    }

    @Override
    public Multi<String> streamResponse(final String input) {
        return Multi.createFrom().item("assistant reply: " + input);
    }
}