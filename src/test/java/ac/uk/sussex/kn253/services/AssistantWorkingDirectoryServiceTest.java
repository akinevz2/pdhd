package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.events.CwdResolvedEvent;

class AssistantWorkingDirectoryServiceTest {

    @Test
    void cwdEventUpdatesAssistantWorkingDirectory() throws Exception {
        final AssistantWorkingDirectoryService service = new AssistantWorkingDirectoryService();
        final Path dir = Files.createTempDirectory("pdhd-assistant-cwd-");

        try {
            service.onCwdResolved(new CwdResolvedEvent(".", dir.toString()));
            assertEquals(dir.toString(), service.getCurrentWorkingDirectory());
        } finally {
            Files.deleteIfExists(dir);
        }
    }
}