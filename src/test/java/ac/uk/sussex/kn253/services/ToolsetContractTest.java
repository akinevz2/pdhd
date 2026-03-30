package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.services.tools.MacroToolModule;
import dev.langchain4j.agent.tool.ToolSpecification;

class ToolsetContractTest {

    @Test
    void requiredToolsAreExposed() {
        final ToolService toolService = new ToolService(
                java.util.List.of(new MacroToolModule()));

        final Set<String> names = toolService.toolSpecifications().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(names.contains("list_subdirectories"));
        assertTrue(names.contains("list_files_recursive"));
        assertTrue(names.contains("change_working_directory"));
        assertTrue(names.contains("analyze_path_detailed"));
        assertTrue(names.contains("summarize_path"));
        assertTrue(names.contains("search_paths"));
        assertTrue(names.contains("get_current_working_directory"));
        assertTrue(names.contains("resolve_path"));
        assertTrue(names.contains("get_path_info"));
        assertTrue(names.contains("list_git_projects"));
        assertTrue(names.contains("get_git_log"));
        assertTrue(names.contains("list_github_projects"));
        assertTrue(names.contains("list_project_entries"));
        assertTrue(names.contains("read_file"));
        assertTrue(names.contains("create_report"));
        assertTrue(names.contains("create_timeline"));
        assertTrue(names.contains("create_plan"));
        assertTrue(names.contains("append_project_todo"));
        assertTrue(names.contains("cache_project_knowledge"));
        assertTrue(names.contains("read_folder_manifest"));
        assertTrue(names.contains("read_project_manifest"));
        assertTrue(names.contains("read_project_knowledge"));
        assertTrue(names.contains("get_session_context"));
        assertTrue(names.contains("open_workspace_canvas"));

        assertTrue(names.size() >= 24, "Expected at least 24 tool specifications");
    }
}
