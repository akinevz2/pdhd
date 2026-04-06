package ac.uk.sussex.kn253.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.events.CwdResolvedEvent;
import ac.uk.sussex.kn253.services.AssistantWorkingDirectoryService;

class FiletypeToolsTest {

    @Test
    void bashFileUsesAssistantWorkingDirectoryForRelativePaths() throws Exception {
        final Path dir = Files.createTempDirectory("pdhd-filetype-cwd-");
        final Path file = dir.resolve("sample.txt");
        Files.writeString(file, "hello");

        final AssistantWorkingDirectoryService cwdService = new AssistantWorkingDirectoryService();
        cwdService.onCwdResolved(new CwdResolvedEvent(".", dir.toString()));

        final FiletypeTools tools = new FiletypeTools();
        tools.assistantWorkingDirectoryService = cwdService;

        final String mimeType = tools.bashFile("sample.txt").toLowerCase();

        assertTrue(mimeType.contains("text/plain"),
                "Expected relative file lookup from synced cwd to resolve as text/plain but got: " + mimeType);

        Files.deleteIfExists(file);
        Files.deleteIfExists(dir);
    }
}