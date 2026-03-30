package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.ollama.SystemPromptBuilder;
import ac.uk.sussex.kn253.services.tools.MacroToolModule;


class SystemPromptBuilderTest {

    @Test
    void buildIncludesProjectKnowledgeRecallAndCachingGuidance() {
        final ToolService toolService = new ToolService(List.of(
                new MacroToolModule()));

        final String prompt = SystemPromptBuilder.build(
                OllamaSettings.DEFAULT_SYSTEM_PROMPT,
                OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT,
                "llama3.2",
                toolService);

        assertTrue(prompt.contains("read_project_knowledge"));
        assertTrue(prompt.contains("cache_project_knowledge"));
        assertTrue(prompt.contains("hasHistory=true"));
        assertTrue(prompt.contains("Treat current-folder metadata as authoritative context"));
        assertTrue(prompt.contains("After completing work that establishes a durable requirement"));
    }

    @Test
    void defaultPromptsMentionKnowledgeRecallAndCaching() {
        assertTrue(OllamaSettings.DEFAULT_SYSTEM_PROMPT.contains("hasHistory=true"));
        assertTrue(OllamaSettings.DEFAULT_SYSTEM_PROMPT.contains("persist a concise tagged knowledge note"));
        assertTrue(OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT.contains("read_project_knowledge"));
        assertTrue(OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT.contains("cache_project_knowledge"));
    }
}