package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolActivityServiceTest {

    @Test
    void capturesPathArgumentForPathBasedTools() {
        final ToolActivityService service = new ToolActivityService();

        service.record("summarize_path", "{\"path\":\"/workspaces/demo\"}", "Directory summary\npath=/workspaces/demo");

        final var event = service.recent(1).get(0);
        assertTrue(event.requestedFiles().contains("/workspaces/demo"));
    }

    @Test
    void capturesPathFromResultWhenArgumentsAreEmpty() {
        final ToolActivityService service = new ToolActivityService();

        service.record("list_subdirectories", "{}", "path=/workspaces/project\nsrc\ntarget");

        final var event = service.recent(1).get(0);
        assertTrue(event.requestedFiles().contains("/workspaces/project"));
    }
}
