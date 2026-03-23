package ac.uk.sussex.kn253.services.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

class ExploreToolsetNoCdiTest {

        @Test
        void listGitProjectsDoesNotFailWithoutCdi() {
                final ExploreToolset toolset = new ExploreToolset();
                final String result = toolset.execute(request("list_git_projects", "{}"), null);

                assertTrue(!result.startsWith("Tool execution failed"),
                                "Expected graceful response without CDI, got: " + result);
                assertTrue(result.startsWith("No git projects found") || result.contains("- "),
                                "Expected either empty-state message or discovered projects, got: " + result);
        }

        @Test
        void listGithubProjectsDoesNotFailWithoutCdi() {
                final ExploreToolset toolset = new ExploreToolset();
                final String result = toolset.execute(request("list_github_projects", "{}"), null);

                assertTrue(!result.startsWith("Tool execution failed"),
                                "Expected graceful response without CDI, got: " + result);
                assertTrue(result.equals("No GitHub projects found in database.") || result.contains("->"),
                                "Expected either empty-state message or discovered GitHub projects, got: " + result);
        }

        @Test
        void navigateToolWorksWithoutCdi() {
                final ExploreToolset toolset = new ExploreToolset();
                final String result = toolset.execute(request("change_working_directory", "{\"path\":\".\"}"), null);

                final String expected = Path.of(".").toAbsolutePath().normalize().toString();
                assertEquals("cwd=" + expected, result);
        }

        private ToolExecutionRequest request(final String name, final String jsonArguments) {
                return ToolExecutionRequest.builder()
                                .name(name)
                                .arguments(jsonArguments)
                                .build();
        }
}
