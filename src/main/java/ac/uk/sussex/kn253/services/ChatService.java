package ac.uk.sussex.kn253.services;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

/**
 * AI service contract for general assistant chat turns.
 */
@RegisterAiService
public interface ChatService {

    String DEFAULT_PROMPT_PREFIX = "assistant> ";

    @SystemMessage("You are a concise software assistant. Answer clearly and avoid unnecessary verbosity.")
    Multi<String> chat(@UserMessage String userMessage);
}