package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.ollama.PromptBuilder;
import ac.uk.sussex.kn253.services.tools.MacroToolModule;

class SystemPromptBuilderTest {

    @Test
    void buildIncludesAllowedClausesPolicyAndPromptSections() {
        final ToolService toolService = new ToolService(List.of(
                new MacroToolModule()));

        final String prompt = PromptBuilder.buildMonolithPrompt(
            PromptBuilder.PromptRequestType.CONVERSATION,
                OllamaSettings.DEFAULT_SYSTEM_PROMPT,
                OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT,
                OllamaSettings.DEFAULT_MODEL_NAME,
            toolService.toolSpecifications());

        assertTrue(prompt.contains("read_project_knowledge"));
        assertTrue(prompt.contains("cache_project_knowledge"));
        assertTrue(prompt.contains("hasHistory=true"));
        assertTrue(prompt.contains("Main assistant instructions:"));
        assertTrue(prompt.contains("Tool agent instructions:"));
        assertTrue(prompt.contains("Allowed clauses:"));
        assertTrue(prompt.contains("Allowed: Tool calls for requests that require concrete project"));
        assertFalse(prompt.contains("Operating rules:"));
    }

    @Test
    void defaultPromptsMentionKnowledgeRecallAndCaching() {
        assertTrue(OllamaSettings.DEFAULT_SYSTEM_PROMPT.contains("hasHistory=true"));
        assertTrue(OllamaSettings.DEFAULT_SYSTEM_PROMPT.contains("persist a concise tagged knowledge note"));
        assertTrue(OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT.contains("read_project_knowledge"));
        assertTrue(OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT.contains("cache_project_knowledge"));
    }
}