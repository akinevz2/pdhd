package ac.uk.sussex.kn253.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AssistantMenuPromptTest {

    @Test
    void shortenPathAbbreviatesAllButLastSegment() {
        assertEquals("/w/d/u/2/pdhd", AssistantMenu.shortenPath("/workspaces/development/uni/2025-project/pdhd"));
    }

    @Test
    void formatUserPromptShowsOnlyPath() {
        assertEquals("/w/d/u/2/pdhd > ",
                AssistantMenu.formatUserPrompt("/workspaces/development/uni/2025-project/pdhd"));
    }

    @Test
    void formatUserPromptFallsBackWhenPathMissing() {
        assertEquals("? > ",
                AssistantMenu.formatUserPrompt(" "));
    }
}