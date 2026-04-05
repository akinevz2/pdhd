package ac.uk.sussex.kn253.services;

import ac.uk.sussex.kn253.tools.FiletypeTools;
import dev.langchain4j.service.*;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService()
public interface AssistantService {

    public static final String DEFAULT_PROMPT_PREFIX = "assistant> ";

    @UserMessage("{{it}}")
    String chat(String userMessage);

    @SystemMessage("Determine whether the given file requires {{support}}. Respond with JSON containing 'required' (boolean) and 'reasoning' (string).")
    SupportResponse requiresSupport(@UserMessage String fileName, @V("support") String supportType);

    @SystemMessage("Return only the lowercase programming language identifier suitable for syntax highlighting (e.g. 'typescript', 'python', 'java', 'yaml'). Return 'text' if unknown. No explanation.")
    @ToolBox(value = FiletypeTools.class)
    String getFileType(@UserMessage String fileName);
}
