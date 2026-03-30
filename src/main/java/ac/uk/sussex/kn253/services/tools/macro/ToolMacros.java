package ac.uk.sussex.kn253.services.tools.macro;

import java.util.*;

public final class ToolMacros {

        public static final ToolMacroDefinition GET_CURRENT_WORKING_DIRECTORY = tool(
                        "get_current_working_directory",
                        "Return the current working directory as an absolute path.",
                        Map.of(),
                        "get_cwd",
                        "get current working directory",
                        "current working directory",
                        "cwd");
        public static final ToolMacroDefinition CHANGE_WORKING_DIRECTORY = tool(
                        "change_working_directory",
                        "Change the assistant working directory only when the user explicitly asks to navigate or switch folders. Supports absolute paths or paths relative to cwd.",
                        Map.of(),
                        "navigate_tool",
                        "change working directory",
                        "change directory",
                        "navigate");
        public static final ToolMacroDefinition RESOLVE_PATH = tool(
                        "resolve_path",
                        "Resolve an absolute or relative path against cwd and return the normalized absolute path.",
                        Map.of(),
                        "resolve path",
                        "normalize path");
        public static final ToolMacroDefinition SEARCH_PATHS = tool(
                        "search_paths",
                        "Search from a directory for likely file or folder candidates matching a partial name. Use this first when the user refers to a vague filesystem target such as frontend, webui, tests, config, or main entry point and you do not yet know the exact path. Do not navigate automatically when multiple plausible matches are returned; summarize the candidates and ask the user to choose.",
                        Map.of(),
                        "find_paths",
                        "search paths",
                        "find paths");
        public static final ToolMacroDefinition GET_PATH_INFO = tool(
                        "get_path_info",
                        "Return basic metadata for a path (exists, type, readable, writability, absolute path).",
                        Map.of(),
                        "path_info",
                        "get path info",
                        "path info");
        public static final ToolMacroDefinition LIST_SUBDIRECTORIES = tool(
                        "list_subdirectories",
                        "List immediate sub-folders for a given absolute or relative path.",
                        Map.of(),
                        "list_folders",
                        "list subdirectories",
                        "list directories");
        public static final ToolMacroDefinition LIST_FILES_RECURSIVE = tool(
                        "list_files_recursive",
                        "List all files under a given folder recursively using paths relative to that folder.",
                        Map.of(),
                        "list_folder",
                        "list files recursive",
                        "list files recursively");
        public static final ToolMacroDefinition ANALYZE_PATH_DETAILED = tool(
                        "analyze_path_detailed",
                        "Provide a detailed analysis of a file or directory path.",
                        Map.of(),
                        "explain_tool",
                        "analyze path detailed",
                        "analyze path");
        public static final ToolMacroDefinition SUMMARIZE_PATH = tool(
                        "summarize_path",
                        "Provide a concise summary of a file or directory path.",
                        Map.of(
                                        "error",
                                        ac.uk.sussex.kn253.services.tools.PathSummaryLlmService.SUMMARY_ERROR_PREFIX,
                                        "unavailable",
                                        ac.uk.sussex.kn253.services.tools.PathSummaryLlmService.SUMMARY_UNAVAILABLE_PREFIX),
                        "summarise_tool",
                        "summarize path",
                        "summarise path");
        public static final ToolMacroDefinition LIST_GIT_PROJECTS = tool(
                        "list_git_projects",
                        "List known projects in the database that have a Git repository attached.",
                        Map.of(),
                        "list git projects");
        public static final ToolMacroDefinition LIST_GITHUB_PROJECTS = tool(
                        "list_github_projects",
                        "List known projects in the database that have GitHub repository metadata attached.",
                        Map.of(),
                        "list github projects");
        public static final ToolMacroDefinition LIST_PROJECT_ENTRIES = tool(
                        "list_project_entries",
                        "List files and folders in a project's directory, optionally under a relative subpath.",
                        Map.of(),
                        "list_files_in_project",
                        "list project entries");
        public static final ToolMacroDefinition GET_GIT_LOG = tool(
                        "get_git_log",
                        "Return recent git commits (one line per commit) for a repository.",
                        Map.of(),
                        "show_git_log",
                        "get git log",
                        "show git log");

        public static final ToolMacroDefinition READ_FILE = tool(
                        "read_file",
                        "Read a UTF-8 text file from a project directory. Optionally limit output to max lines.",
                        Map.of(),
                        "read file");

        public static final ToolMacroDefinition WRITE_FILE = tool(
                        "write_file",
                        "Write a UTF-8 text file within the project directory.",
                        Map.of(),
                        "write file");
        public static final ToolMacroDefinition CREATE_REPORT = tool(
                        "create_report",
                        "Create a markdown report under <project>/.pdhd/reports.",
                        Map.of(),
                        "create report");
        public static final ToolMacroDefinition CREATE_TIMELINE = tool(
                        "create_timeline",
                        "Create a timeline markdown under <project>/.pdhd/timelines.",
                        Map.of(),
                        "create timeline");
        public static final ToolMacroDefinition CREATE_PLAN = tool(
                        "create_plan",
                        "Create an execution plan markdown under <project>/.pdhd/plans.",
                        Map.of(),
                        "create plan");
        public static final ToolMacroDefinition APPEND_PROJECT_TODO = tool(
                        "append_project_todo",
                        "Append a todo entry to <project>/TODO.md.",
                        Map.of(),
                        "create_todo_in_project",
                        "append project todo");
        public static final ToolMacroDefinition CACHE_PROJECT_KNOWLEDGE = tool(
                        "cache_project_knowledge",
                        "Append a tagged knowledge note to the persistent project cache. Use this to remember important user requests, constraints, or decisions for later recall.",
                        Map.of(),
                        "cache project knowledge");

        public static final ToolMacroDefinition READ_FOLDER_MANIFEST = tool(
                        "read_folder_manifest",
                        "Read a specific folder recursively and return an evidence-based folder manifest (discovered files/folders + sampled exact contents from files in that folder tree). Use this when the user asks to summarise a folder or subfolder. Do NOT use this as a whole-project summary tool; use read_project_manifest for that. Do not claim content for files not included in the sampled-content section.",
                        Map.of(),
                        "read folder manifest");
        public static final ToolMacroDefinition READ_PROJECT_MANIFEST = tool(
                        "read_project_manifest",
                        "Read key project identity files (README, package.json, pom.xml, Cargo.toml, go.mod, requirements.txt, Makefile, etc.) from a directory and inspect src/ recursively (file list + sampled exact contents) to understand the project's purpose and technology stack. Use ONLY for summarising or explaining the entire project. Do NOT use for summarising individual files or folders - use read_folder_manifest (preferred) or summarize_path for those cases. If the user asks to summarise a folder or file, do NOT call this tool. Parameter 'path' is required unless using the current working directory.",
                        Map.of(),
                        "read project manifest");
        public static final ToolMacroDefinition READ_PROJECT_KNOWLEDGE = tool(
                        "read_project_knowledge",
                        "Read cached tagged project knowledge remembered from earlier user queries or prior analysis. Use this to recall stored constraints, decisions, requirements, or bug notes before repeating work.",
                        Map.of(),
                        "read project knowledge");
        public static final ToolMacroDefinition GET_SESSION_CONTEXT = tool(
                        "get_session_context",
                        "Return the current working directory and the recent tool call history for this session. Use this to reflect on your current context, especially at the start of a new task or after changing directories.",
                        Map.of(),
                        "get session context");
        public static final ToolMacroDefinition OPEN_WORKSPACE_CANVAS = tool(
                        "open_workspace_canvas",
                        "Request opening a workspace project canvas in the web UI for a given path. Use only when the user explicitly asks to open a project/folder/file in canvas. Parameter 'path' is required unless using the current working directory.",
                        Map.of(),
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

        private static ToolMacroDefinition tool(
                        final String name,
                        final String description,
                        final Map<String, String> signals,
                        final String... keyphrases) {
                return new ToolMacroDefinition(name, description, signals, List.of(keyphrases));
        }

        private static String normalize(final String value) {
                return value.trim().toLowerCase(Locale.ROOT);
        }
}