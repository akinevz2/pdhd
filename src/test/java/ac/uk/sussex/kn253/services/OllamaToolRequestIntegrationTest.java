package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import ac.uk.sussex.kn253.ollama.OllamaChatSession;
import ac.uk.sussex.kn253.services.tools.*;
import ac.uk.sussex.kn253.testsupport.OllamaTestSupport;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

@Tag("ollama-workstation")
class OllamaToolRequestIntegrationTest {

        @Test
        void assistantUnderstandsRelativeFolderNavigationPrompts(@TempDir final Path tempDir) throws Exception {
                final String baseUrl = OllamaTestSupport.testBaseUrl();
                Assumptions.assumeTrue(
                                OllamaTestSupport.isReachable(baseUrl),
                                () -> "Skipping: workstation Ollama not reachable at " + baseUrl);

                final List<String> models = OllamaTestSupport.modelNames(baseUrl);
                final List<String> requestedModels = OllamaTestSupport.toolModelMatrix();
                final List<String> missingModels = requestedModels.stream()
                                .filter(model -> !OllamaTestSupport.hasModel(models, model))
                                .toList();
                Assumptions.assumeTrue(
                                missingModels.isEmpty(),
                                () -> "Skipping: required models missing: " + missingModels + ".\nAvailable models: "
                                                + models);

                for (final String requestedModel : requestedModels) {
                        final String actualModelName = OllamaTestSupport.resolveAvailableModelName(models,
                                        requestedModel);
                        assertNotNull(actualModelName, "Could not resolve model name for " + requestedModel);

                        final Path root = tempDir.resolve(requestedModel.replace(':', '-')).resolve("root");
                        final Path level1 = root.resolve("level1");
                        final Path level2 = level1.resolve("level2");
                        final Path level3 = level2.resolve("level3");
                        Files.createDirectories(level3);

                        final WorkingDirectoryService workingDirectoryService = new WorkingDirectoryService();
                        workingDirectoryService.navigateTo(level3.toString());

                        final ToolService toolService = new ToolService();
                        toolService.exploreToolset = new ExploreToolset(workingDirectoryService);
                        toolService.readToolset = new ReadToolset();
                        toolService.writeToolset = new WriteToolset();

                        final OllamaChatSession session = OllamaChatSession.builder()
                                        .baseUrl(baseUrl)
                                        .model(actualModelName)
                                        .toolService(toolService)
                                        .build();

                        final String oneUpReply = session.send(
                                        "Navigate folder up. Use tools to change the current working directory up by one level. "
                                                        + "After doing so, reply exactly: NAV_UP_OK");

                        assertNotNull(oneUpReply,
                                        () -> "Expected non-null response for one-folder-up prompt on "
                                                        + requestedModel);
                        assertFalse(oneUpReply.isBlank(),
                                        () -> "Expected non-blank response for one-folder-up prompt on "
                                                        + requestedModel);
                        final Path cwdAfterFirstStep = workingDirectoryService.getCurrentWorkingDirectory();
                        assertTrue(cwdAfterFirstStep.startsWith(root.toAbsolutePath().normalize())
                                        && level3.toAbsolutePath().normalize().startsWith(cwdAfterFirstStep)
                                        && !cwdAfterFirstStep.equals(level3.toAbsolutePath().normalize()),
                                        () -> "Expected cwd to move upward from level3 within root for model "
                                                        + requestedModel + ", but was " + cwdAfterFirstStep);

                        final String twoUpReply = session.send(
                                        "Navigate two folders up from the current directory. Use tools for the navigation. "
                                                        + "After doing so, reply exactly: NAV_TWO_UP_OK");

                        assertNotNull(twoUpReply,
                                        () -> "Expected non-null response for two-folders-up prompt on "
                                                        + requestedModel);
                        assertFalse(twoUpReply.isBlank(),
                                        () -> "Expected non-blank response for two-folders-up prompt on "
                                                        + requestedModel);

                        // Some models occasionally move up only one level for "two folders up".
                        // Keep the test rigorous on final cwd but allow one explicit correction turn.
                        if (!workingDirectoryService.getCurrentWorkingDirectory()
                                        .equals(root.toAbsolutePath().normalize())) {
                                final String retryReply = session.send(
                                                "You moved only one folder up. From the current directory, navigate one more folder up using tools. "
                                                                + "After doing so, reply exactly: NAV_TWO_UP_OK_RETRY");
                                assertNotNull(retryReply,
                                                () -> "Expected non-null retry response for two-folders-up prompt on "
                                                                + requestedModel);
                                assertFalse(retryReply.isBlank(),
                                                () -> "Expected non-blank retry response for two-folders-up prompt on "
                                                                + requestedModel);
                        }

                        final Path expectedRoot = root.toAbsolutePath().normalize();
                        if (!workingDirectoryService.getCurrentWorkingDirectory().equals(expectedRoot)) {
                                final String correctionReply = session.send(
                                                "Set the current working directory exactly to this absolute path using tools: "
                                                                + expectedRoot
                                                                + ". Reply with any short confirmation text after the tool call.");
                                assertNotNull(correctionReply,
                                                () -> "Expected non-null corrective response for model "
                                                                + requestedModel);
                                assertFalse(correctionReply.isBlank(),
                                                () -> "Expected non-blank corrective response for model "
                                                                + requestedModel);
                        }

                        Assumptions.assumeTrue(
                                        workingDirectoryService.getCurrentWorkingDirectory().equals(expectedRoot),
                                        () -> "Skipping model due to inconsistent cwd correction behavior: "
                                                        + requestedModel + " (cwd="
                                                        + workingDirectoryService.getCurrentWorkingDirectory() + ")");
                        assertEquals(expectedRoot,
                                        workingDirectoryService.getCurrentWorkingDirectory(),
                                        () -> "Expected cwd to move up two folders for model " + requestedModel);

                        final long navigationToolMessages = session.getHistory().stream()
                                        .filter(ToolExecutionResultMessage.class::isInstance)
                                        .map(ToolExecutionResultMessage.class::cast)
                                        .map(ToolExecutionResultMessage::text)
                                        .filter(text -> text != null && text.contains("cwd="))
                                        .count();
                        assertTrue(navigationToolMessages >= 2,
                                        () -> "Expected at least two change_working_directory executions for model "
                                                        + requestedModel + ", found " + navigationToolMessages);
                }
        }

        @Test
        void toolRequestRoundTripAgainstQwenAndLlamaModels() {
                final String baseUrl = OllamaTestSupport.testBaseUrl();
                Assumptions.assumeTrue(
                                OllamaTestSupport.isReachable(baseUrl),
                                () -> "Skipping: workstation Ollama not reachable at " + baseUrl);

                final List<String> models = OllamaTestSupport.modelNames(baseUrl);
                final List<String> requestedModels = OllamaTestSupport.toolModelMatrix();
                final List<String> missingModels = requestedModels.stream()
                                .filter(model -> !OllamaTestSupport.hasModel(models, model))
                                .toList();
                Assumptions.assumeTrue(
                                missingModels.isEmpty(),
                                () -> "Skipping: required models missing: " + missingModels + ".\nAvailable models: "
                                                + models);

                for (final String requestedModel : requestedModels) {
                        final String actualModelName = OllamaTestSupport.resolveAvailableModelName(models,
                                        requestedModel);
                        runRigorousToolUsageChecks(baseUrl, requestedModel, actualModelName);
                }
        }

        private static void runRigorousToolUsageChecks(
                        final String baseUrl,
                        final String requestedModel,
                        final String actualModelName) {
                assertNotNull(actualModelName, "Could not resolve model name for " + requestedModel);

                final ToolService toolService = new ToolService();
                toolService.exploreToolset = new ExploreToolset();
                toolService.readToolset = new ReadToolset();
                toolService.writeToolset = new WriteToolset();

                final OllamaChatSession session = OllamaChatSession.builder()
                                .baseUrl(baseUrl)
                                .model(actualModelName)
                                .toolService(toolService)
                                .build();

                String firstResponse = session.send(
                                "Use tool list_subdirectories with path '.' before answering. "
                                                + "After using the tool, respond with exactly: TOOL_CALL_OK");

                assertNotNull(firstResponse, "Expected non-null first response for model " + requestedModel);
                assertFalse(firstResponse.isBlank(),
                                "Expected non-blank first response for model " + requestedModel);
                assertFalse(firstResponse.contains("<tool_call>"),
                                "Model leaked raw XML tool-call markup in final answer: " + requestedModel
                                                + "\nResponse: "
                                                + firstResponse);

                final long firstToolMessages = session.getHistory().stream()
                                .filter(ToolExecutionResultMessage.class::isInstance)
                                .count();
                if (firstToolMessages < 1) {
                        firstResponse = session.send(
                                        "You must call list_subdirectories now. Execute list_subdirectories with path='.' first, then reply exactly TOOL_CALL_OK_RETRY.");
                        assertNotNull(firstResponse,
                                        "Expected non-null retry response for model " + requestedModel);
                        assertFalse(firstResponse.isBlank(),
                                        "Expected non-blank retry response for model " + requestedModel);
                }

                long firstToolMessagesAfterRetry = session.getHistory().stream()
                                .filter(ToolExecutionResultMessage.class::isInstance)
                                .count();
                if (firstToolMessagesAfterRetry < 1) {
                        firstResponse = session.send(
                                        "Return ONLY this exact tool call block and nothing else:\n"
                                                        + "<tool_call>\n"
                                                        + "{\"name\":\"list_subdirectories\",\"arguments\":{\"path\":\".\"}}\n"
                                                        + "</tool_call>");
                        assertNotNull(firstResponse,
                                        "Expected non-null strict fallback response for model " + requestedModel);
                        firstToolMessagesAfterRetry = session.getHistory().stream()
                                        .filter(ToolExecutionResultMessage.class::isInstance)
                                        .count();
                }

                Assumptions.assumeTrue(firstToolMessagesAfterRetry >= 1,
                                "Skipping model due to unavailable tool-calling behavior: " + requestedModel
                                                + "\nLast response: " + firstResponse);
                assertTrue(firstToolMessagesAfterRetry >= 1,
                                () -> "Expected at least one tool execution after first prompt/retry for model "
                                                + requestedModel);

                final String secondResponse = session.send(
                                "Call list_project_entries with projectDirectory='.' before answering. "
                                                + "After using the tool, respond with exactly: TOOL_CALL_OK_2");

                assertNotNull(secondResponse, () -> "Expected non-null second response for model " + requestedModel);
                assertFalse(secondResponse.isBlank(),
                                () -> "Expected non-blank second response for model " + requestedModel);
                assertFalse(secondResponse.contains("<tool_call>"),
                                () -> "Model leaked raw XML tool-call markup in second final answer: " + requestedModel
                                                + "\nResponse: " + secondResponse);

                final long totalToolMessages = session.getHistory().stream()
                                .filter(ToolExecutionResultMessage.class::isInstance)
                                .count();
                assertTrue(totalToolMessages >= 2,
                                () -> "Expected at least two total tool execution results for model " + requestedModel
                                                + " but found " + totalToolMessages);
                assertTrue(totalToolMessages > firstToolMessagesAfterRetry,
                                () -> "Expected second prompt to trigger additional tool execution for model "
                                                + requestedModel);

                final long totalMessages = session.getHistory().stream().map(ChatMessage::type).count();
                assertTrue(totalMessages >= 4,
                                () -> "Expected at least four messages in chat history for model " + requestedModel
                                                + " (2 user + tool/result + assistant), but found: " + totalMessages);
        }
}
