package ac.uk.sussex.kn253.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.services.CwdService;

class FiletypeToolsTest {

    @Test
    void bashFileUsesCwdServiceForRelativePaths() throws Exception {
        final Path dir = Files.createTempDirectory("pdhd-filetype-cwd-");
        final Path file = dir.resolve("sample.txt");
        Files.writeString(file, "hello");

        final CwdService cwdService = new CwdService();
        cwdService.setCurrentWorkingDirectory(dir.toString());

        final FiletypeTools tools = new FiletypeTools();
        tools.cwdService = cwdService;

        final String mimeType = tools.bashFile("sample.txt").toLowerCase();

        assertTrue(mimeType.contains("text/plain"),
                "Expected relative file lookup from synced cwd to resolve as text/plain but got: " + mimeType);

        Files.deleteIfExists(file);
        Files.deleteIfExists(dir);
    }
}