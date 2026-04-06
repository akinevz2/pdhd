package ac.uk.sussex.kn253.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AssistantMenuPromptTest {

    @Test
    void shortenPathAbbreviatesAllButLastSegment() {
        assertEquals("/w/d/u/2/pdhd", AssistantMenu.shortenPath("/workspaces/development/uni/2025-project/pdhd"));
    }

    @Test
    void formatUserPromptPrependsModelName() {
        assertEquals("llama3.2:/w/d/u/2/pdhd user> ",
                AssistantMenu.formatUserPrompt("llama3.2", "/workspaces/development/uni/2025-project/pdhd"));
    }

    @Test
    void formatUserPromptFallsBackWhenModelMissing() {
        assertEquals("assistant:/w/d/u/2/pdhd user> ",
                AssistantMenu.formatUserPrompt(" ", "/workspaces/development/uni/2025-project/pdhd"));
    }
}