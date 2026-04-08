package ac.uk.sussex.kn253.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.services.CwdService;

class FiletypeToolsTest {

    @Test
    void bashFileRejectsBlankPath() {
        final FiletypeTools tools = new FiletypeTools();

        final String result = tools.bashFile("   ");

        assertTrue(result.contains("filePath must not be blank"));
    }

    @Test
    void bashFileUsesCwdServiceForRelativePaths() throws Exception {
        Assumptions.assumeTrue(hasFileCommand(), "host does not provide the `file` command");

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
        assertFalse(mimeType.contains("cannot open"),
                "Relative lookup should resolve inside cwd rather than fail to find the file: " + mimeType);

        Files.deleteIfExists(file);
        Files.deleteIfExists(dir);
    }

    private static boolean hasFileCommand() {
        try {
            final Process process = new ProcessBuilder("which", "file").start();
            return process.waitFor() == 0;
        } catch (final IOException | InterruptedException e) {
            return false;
        }
    }
}