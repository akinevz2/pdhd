package ac.uk.sussex.kn253.services.ai;

import dev.langchain4j.service.*;

@SystemMessage("""
        You are a project analysis assistant.
        Use tools when needed to read files and inspect workspace context.
        """)
public interface ProjectAssistant {

    @UserMessage("{{message}}")
    TokenStream stream(@MemoryId String memoryId, @V("message") String message);
}
