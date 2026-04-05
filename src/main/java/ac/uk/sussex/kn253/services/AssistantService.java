package ac.uk.sussex.kn253.services;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService()
public interface AssistantService {

    public static final String DEFAULT_PROMPT_PREFIX = "assistant> ";

    @UserMessage("{{it}}")
    String chat(String userMessage);

}
