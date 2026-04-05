package ac.uk.sussex.kn253.services;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.smallrye.mutiny.Multi;

public interface IChatService {

    StreamingChatModel model();

    EmbeddingModel embeddingModel();

    Multi<String> streamResponse(String input);
}
