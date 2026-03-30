package ac.uk.sussex.kn253.services.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

class MacroToolModuleReadTest {

    private final MacroToolModule toolset = new MacroToolModule();

    @Test
    void readFileReturnsContentWithLineLimit(@TempDir final Path tempDir) throws Exception {
        final Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "one\ntwo\nthree\n");

        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"filePath\":\"sample.txt\",\"maxLines\":2}";
        final String result = toolset.execute(request("read_file", args), null);

        assertEquals("one\ntwo", result);
    }

    @Test
    void readFileRejectsPathTraversal(@TempDir final Path tempDir) {
        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"filePath\":\"../outside.txt\"}";
        final String result = toolset.execute(request("read_file", args), null);

        assertTrue(result.contains("outside project directory"));
    }

    @Test
    void readFileHandlesMissingFile(@TempDir final Path tempDir) {
        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"filePath\":\"missing.txt\"}";
        final String result = toolset.execute(request("read_file", args), null);

        assertTrue(result.contains("File not found"));
    }

    @Test
    void readFileValidatesMaxLines(@TempDir final Path tempDir) throws Exception {
        final Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "one\n");

        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"filePath\":\"sample.txt\",\"maxLines\":0}";
        final String result = toolset.execute(request("read_file", args), null);

        assertTrue(result.contains("Invalid maxLines"));
    }

    @Test
    void readFileUnknownToolMessage() {
        final String result = toolset.execute(request("unknown", "{}"), null);
        assertTrue(result.contains("Unknown tool"));
    }

    private ToolExecutionRequest request(final String name, final String jsonArguments) {
        return ToolExecutionRequest.builder().name(name).arguments(jsonArguments).build();
    }

    private String escape(final Path path) {
        return path.toString().replace("\\", "\\\\");
    }
}
