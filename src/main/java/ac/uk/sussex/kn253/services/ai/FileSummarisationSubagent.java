package ac.uk.sussex.kn253.services.ai;

import dev.langchain4j.service.*;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface FileSummarisationSubagent {

    @SystemMessage("""
            You are a technical summarisation agent.
            You receive raw file contents and must return only valid JSON.
            The JSON schema is:
            {
              "purpose": string,
              "keyComponents": string[],
              "dependencies": string[]
            }
            Focus on factual extraction from content only.
            Keep values concise. Do not speculate.
            Do not include markdown fences or any text outside JSON.
            """)
    String summarise(@UserMessage String fileContents, @V("Filepath of the file being summarised") String filePath);

    @SystemMessage("""
            You are a technical summarisation agent.
            You receive file summaries and must return only valid JSON.
            The JSON schema is:
            {
              "purpose": string,
              "keyComponents": string[],
              "dependencies": string[]
            }
            Focus on factual folder-level synthesis from provided summaries only.
            Keep values concise. Do not speculate.
            Do not include markdown fences or any text outside JSON.
            """)
    String summariseFolder(@UserMessage String folderSummaries,
            @V("Path of the folder being summarised") String folderPath);
}