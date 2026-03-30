package ac.uk.sussex.kn253.services.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

class MacroToolModuleWriteTest {

    private final MacroToolModule toolset = new MacroToolModule();

    @Test
    void writeFileCreatesAndAppends(@TempDir final Path tempDir) throws Exception {
        final String baseArgs = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"filePath\":\"notes/todo.txt\",\"content\":\"one\\n\"}";
        toolset.execute(request("write_file", baseArgs), null);

        final String appendArgs = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"filePath\":\"notes/todo.txt\",\"content\":\"two\\n\",\"append\":true}";
        final String appendResult = toolset.execute(request("write_file", appendArgs), null);

        assertTrue(appendResult.contains("File written"));
        final String content = Files.readString(tempDir.resolve("notes/todo.txt"));
        assertEquals("one\ntwo\n", content);
    }

    @Test
    void writeFileRejectsPathTraversal(@TempDir final Path tempDir) {
        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"filePath\":\"../oops.txt\",\"content\":\"bad\"}";
        final String result = toolset.execute(request("write_file", args), null);

        assertTrue(result.contains("outside project directory"));
    }

    @Test
    void createReportCreatesMarkdown(@TempDir final Path tempDir) throws Exception {
        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"title\":\"Sprint Report\",\"content\":\"Done\"}";
        final String result = toolset.execute(request("create_report", args), null);

        assertTrue(result.contains("Report created"));
        final Path output = tempDir.resolve(".pdhd/reports/sprint-report.md");
        assertTrue(Files.exists(output));
        assertTrue(Files.readString(output).contains("# Sprint Report"));
    }

    @Test
    void createTimelineCreatesOrderedList(@TempDir final Path tempDir) throws Exception {
        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"title\":\"Roadmap\",\"milestones\":[\"Start\",\"Finish\"]}";
        final String result = toolset.execute(request("create_timeline", args), null);

        assertTrue(result.contains("Timeline created"));
        final Path output = tempDir.resolve(".pdhd/timelines/roadmap.md");
        final String content = Files.readString(output);
        assertTrue(content.contains("1. Start"));
        assertTrue(content.contains("2. Finish"));
    }

    @Test
    void createPlanCreatesOrderedSteps(@TempDir final Path tempDir) throws Exception {
        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"title\":\"Execution Plan\",\"steps\":[\"Analyse\",\"Ship\"]}";
        final String result = toolset.execute(request("create_plan", args), null);

        assertTrue(result.contains("Plan created"));
        final Path output = tempDir.resolve(".pdhd/plans/execution-plan.md");
        final String content = Files.readString(output);
        assertTrue(content.contains("1. Analyse"));
        assertTrue(content.contains("2. Ship"));
    }

    @Test
    void createPlanWritesFullMarkdownContentWhenProvided(@TempDir final Path tempDir) throws Exception {
        final String markdown = "# Execution Plan\\n\\n## Goals\\n\\n- Analyse\\n- Ship\\n";
        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"title\":\"Execution Plan\",\"content\":\"" + markdown + "\"}";
        final String result = toolset.execute(request("create_plan", args), null);

        assertTrue(result.contains("Plan created"));
        final Path output = tempDir.resolve(".pdhd/plans/execution-plan.md");
        final String content = Files.readString(output);
        assertEquals("# Execution Plan\n\n## Goals\n\n- Analyse\n- Ship\n", content);
    }

    @Test
    void createPlanParsesStepsWhenProvidedAsMarkdownString(@TempDir final Path tempDir) throws Exception {
        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"title\":\"Execution Plan\",\"steps\":\"1. Analyse\\n2. Ship\"}";
        final String result = toolset.execute(request("create_plan", args), null);

        assertTrue(result.contains("Plan created"));
        final Path output = tempDir.resolve(".pdhd/plans/execution-plan.md");
        final String content = Files.readString(output);
        assertTrue(content.contains("1. Analyse"));
        assertTrue(content.contains("2. Ship"));
    }

    @Test
    void createTodoAppendsToTodoFile(@TempDir final Path tempDir) throws Exception {
        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"todo\":\"Refactor parser\"}";
        final String result = toolset.execute(request("append_project_todo", args), null);

        assertTrue(result.contains("TODO added"));
        final String content = Files.readString(tempDir.resolve("TODO.md"));
        assertTrue(content.contains("Refactor parser"));
    }

    @Test
    void writeToolsetUnknownToolMessage() {
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
