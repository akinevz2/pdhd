package ac.uk.sussex.kn253.services.tools.macro;

import java.util.*;

public final class ToolMacros {

    public static final ToolMacroDefinition GET_CURRENT_WORKING_DIRECTORY = tool(
            "get_current_working_directory",
            "get_cwd",
            "get current working directory",
            "current working directory",
            "cwd");
    public static final ToolMacroDefinition CHANGE_WORKING_DIRECTORY = tool(
            "change_working_directory",
            "navigate_tool",
            "change working directory",
            "change directory",
            "navigate");
    public static final ToolMacroDefinition RESOLVE_PATH = tool(
            "resolve_path",
            "resolve path",
            "normalize path");
    public static final ToolMacroDefinition SEARCH_PATHS = tool(
            "search_paths",
            "find_paths",
            "search paths",
            "find paths");
    public static final ToolMacroDefinition GET_PATH_INFO = tool(
            "get_path_info",
            "path_info",
            "get path info",
            "path info");
    public static final ToolMacroDefinition LIST_SUBDIRECTORIES = tool(
            "list_subdirectories",
            "list_folders",
            "list subdirectories",
            "list directories");
    public static final ToolMacroDefinition LIST_FILES_RECURSIVE = tool(
            "list_files_recursive",
            "list_folder",
            "list files recursive",
            "list files recursively");
    public static final ToolMacroDefinition ANALYZE_PATH_DETAILED = tool(
            "analyze_path_detailed",
            "explain_tool",
            "analyze path detailed",
            "analyze path");
    public static final ToolMacroDefinition SUMMARIZE_PATH = tool(
            "summarize_path",
            "summarise_tool",
            "summarize path",
            "summarise path");
    public static final ToolMacroDefinition LIST_GIT_PROJECTS = tool(
            "list_git_projects",
            "list git projects");
    public static final ToolMacroDefinition LIST_GITHUB_PROJECTS = tool(
            "list_github_projects",
            "list github projects");
    public static final ToolMacroDefinition LIST_PROJECT_ENTRIES = tool(
            "list_project_entries",
            "list_files_in_project",
            "list project entries");
    public static final ToolMacroDefinition GET_GIT_LOG = tool(
            "get_git_log",
            "show_git_log",
            "get git log",
            "show git log");

    public static final ToolMacroDefinition READ_FILE = tool(
            "read_file",
            "read file");

    public static final ToolMacroDefinition WRITE_FILE = tool(
            "write_file",
            "write file");
    public static final ToolMacroDefinition CREATE_REPORT = tool(
            "create_report",
            "create report");
    public static final ToolMacroDefinition CREATE_TIMELINE = tool(
            "create_timeline",
            "create timeline");
    public static final ToolMacroDefinition CREATE_PLAN = tool(
            "create_plan",
            "create plan");
    public static final ToolMacroDefinition APPEND_PROJECT_TODO = tool(
            "append_project_todo",
            "create_todo_in_project",
            "append project todo");
    public static final ToolMacroDefinition CACHE_PROJECT_KNOWLEDGE = tool(
            "cache_project_knowledge",
            "cache project knowledge");

    public static final ToolMacroDefinition READ_FOLDER_MANIFEST = tool(
            "read_folder_manifest",
            "read folder manifest");
    public static final ToolMacroDefinition READ_PROJECT_MANIFEST = tool(
            "read_project_manifest",
            "read project manifest");
    public static final ToolMacroDefinition READ_PROJECT_KNOWLEDGE = tool(
            "read_project_knowledge",
            "read project knowledge");
    public static final ToolMacroDefinition GET_SESSION_CONTEXT = tool(
            "get_session_context",
            "get session context");
    public static final ToolMacroDefinition OPEN_WORKSPACE_CANVAS = tool(
            "open_workspace_canvas",
            "open workspace canvas");

    private ToolMacros() {
    }

    public static String canonicalName(final String rawName, final Collection<ToolMacroDefinition> definitions) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }

        final String normalized = normalize(rawName);
        for (final ToolMacroDefinition definition : definitions) {
            if (normalize(definition.name()).equals(normalized)) {
                return definition.name();
            }
            for (final String keyphrase : definition.invocationKeyphrases()) {
                if (normalize(keyphrase).equals(normalized)) {
                    return definition.name();
                }
            }
        }
        return rawName;
    }

    public static Map<String, String> aliasIndex(final Collection<ToolMacroDefinition> definitions) {
        final Map<String, String> aliases = new LinkedHashMap<>();
        for (final ToolMacroDefinition definition : definitions) {
            aliases.put(normalize(definition.name()), definition.name());
            for (final String keyphrase : definition.invocationKeyphrases()) {
                aliases.put(normalize(keyphrase), definition.name());
            }
        }
        return aliases;
    }

    private static ToolMacroDefinition tool(final String name, final String... keyphrases) {
        return new ToolMacroDefinition(name, List.of(keyphrases));
    }

    private static String normalize(final String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}