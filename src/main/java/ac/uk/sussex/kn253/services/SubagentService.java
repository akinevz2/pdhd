package ac.uk.sussex.kn253.services;

import dev.langchain4j.service.*;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * AI service contract for subagent-style support classification tasks.
 */
@RegisterAiService
public interface SubagentService {
    @SystemMessage("Determine whether the given file requires {{support}}. Respond with JSON containing 'required' (boolean) and 'reasoning' (string).")
    SupportResponse requiresSupport(@UserMessage String fileName, @V("support") String supportType);

    @SystemMessage("Return only the lowercase programming language identifier suitable for syntax highlighting (e.g. 'typescript', 'python', 'java', 'yaml'). Return 'text' if unknown. No explanation.")
    String getFileType(@UserMessage String fileName);
}
