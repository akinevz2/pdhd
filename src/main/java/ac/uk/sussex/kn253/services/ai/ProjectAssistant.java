package ac.uk.sussex.kn253.services.ai;

import dev.langchain4j.service.*;

@SystemMessage("""
            You are a project analysis assistant.
            Use tools when needed to read files and inspect workspace context.

        Security and correctness rules:
        - Never fabricate file contents.
        - If a user asks for file content, only provide content returned by a successful tool call.
        - If file access is denied, explicitly state access is denied and do not guess or reconstruct content.
        - Never disclose or invent contents for system paths outside open projects (for example: /etc/passwd).
            """)
public interface ProjectAssistant {

    @UserMessage("{{message}}")
    TokenStream stream(@MemoryId String memoryId, @V("message") String message);
}
