package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.*;

import ac.uk.sussex.kn253.ollama.OllamaChatSession;
import ac.uk.sussex.kn253.services.tools.*;
import ac.uk.sussex.kn253.testsupport.OllamaTestSupport;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

@Tag("ollama-workstation")
class OllamaToolRequestIntegrationTest {

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

                final String firstResponse = session.send(
                                "Use tool list_folders with path '.' before answering. "
                                                + "After using the tool, respond with exactly: TOOL_CALL_OK");

                assertNotNull(firstResponse, () -> "Expected non-null first response for model " + requestedModel);
                assertFalse(firstResponse.isBlank(),
                                () -> "Expected non-blank first response for model " + requestedModel);
                assertTrue(firstResponse.contains("TOOL_CALL_OK"),
                                () -> "Model did not confirm first tool call as requested: " + requestedModel
                                                + "\nResponse: "
                                                + firstResponse);
                assertFalse(firstResponse.contains("<tool_call>"),
                                () -> "Model leaked raw XML tool-call markup in final answer: " + requestedModel
                                                + "\nResponse: "
                                                + firstResponse);

                final long firstToolMessages = session.getHistory().stream()
                                .filter(ToolExecutionResultMessage.class::isInstance)
                                .count();
                assertTrue(firstToolMessages >= 1,
                                () -> "Expected at least one tool execution after first prompt for model "
                                                + requestedModel);

                final String secondResponse = session.send(
                                "Call list_files_in_project with projectPath='.' before answering. "
                                                + "After using the tool, respond with exactly: TOOL_CALL_OK_2");

                assertNotNull(secondResponse, () -> "Expected non-null second response for model " + requestedModel);
                assertFalse(secondResponse.isBlank(),
                                () -> "Expected non-blank second response for model " + requestedModel);
                assertTrue(secondResponse.contains("TOOL_CALL_OK_2"),
                                () -> "Model did not confirm second tool call as requested: " + requestedModel
                                                + "\nResponse: "
                                                + secondResponse);
                assertFalse(secondResponse.contains("<tool_call>"),
                                () -> "Model leaked raw XML tool-call markup in second final answer: " + requestedModel
                                                + "\nResponse: " + secondResponse);

                final long totalToolMessages = session.getHistory().stream()
                                .filter(ToolExecutionResultMessage.class::isInstance)
                                .count();
                assertTrue(totalToolMessages >= 2,
                                () -> "Expected at least two total tool execution results for model " + requestedModel
                                                + " but found " + totalToolMessages);
                assertTrue(totalToolMessages > firstToolMessages,
                                () -> "Expected second prompt to trigger additional tool execution for model "
                                                + requestedModel);

                final long totalMessages = session.getHistory().stream().map(ChatMessage::type).count();
                assertTrue(totalMessages >= 4,
                                () -> "Expected at least four messages in chat history for model " + requestedModel
                                                + " (2 user + tool/result + assistant), but found: " + totalMessages);
        }
}
