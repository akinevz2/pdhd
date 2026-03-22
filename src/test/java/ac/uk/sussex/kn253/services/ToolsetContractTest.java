package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.services.tools.*;
import dev.langchain4j.agent.tool.ToolSpecification;

class ToolsetContractTest {

    @Test
    void requiredToolsAreExposed() {
        final ToolService toolService = new ToolService();
        toolService.exploreToolset = new ExploreToolset();
        toolService.readToolset = new ReadToolset();
        toolService.writeToolset = new WriteToolset();

        final Set<String> names = toolService.toolSpecifications().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(names.contains("list_folders"));
        assertTrue(names.contains("get_cwd"));
        assertTrue(names.contains("resolve_path"));
        assertTrue(names.contains("path_info"));
        assertTrue(names.contains("list_git_projects"));
        assertTrue(names.contains("list_github_projects"));
        assertTrue(names.contains("list_files_in_project"));
        assertTrue(names.contains("read_file"));
        assertTrue(names.contains("create_report"));
        assertTrue(names.contains("create_timeline"));
        assertTrue(names.contains("create_plan"));
        assertTrue(names.contains("create_todo_in_project"));

        assertTrue(names.size() >= 12, "Expected at least 12 tool specifications");
    }
}
