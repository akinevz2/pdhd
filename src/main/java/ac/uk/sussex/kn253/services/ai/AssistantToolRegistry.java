package ac.uk.sussex.kn253.services.ai;

import java.util.*;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.tools.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssistantToolRegistry {

        @Inject
        WorkspaceContextTools workspaceContextTools;

        @Inject
        ReadFileTools readFileTools;

        @Inject
        WebSearchTools webSearchTools;

        @Inject
        GitMetadataTools gitMetadataTools;

        @Inject
        ObjectMapper objectMapper;

        public List<ToolSpecification> toolSpecifications() {
                final List<ToolSpecification> specs = new ArrayList<>();
                specs.add(listDirectoryContentsSpec());
                specs.add(changeWorkingDirectorySpec());
                specs.add(listFilesRecursiveSpec());
                specs.add(analyzePathDetailedSpec());
                specs.add(summarizePathSpec());
                specs.add(readFileSpec());
                specs.add(searchWebSpec());
                specs.add(getRepositoryStatusSpec());
                specs.add(getRecentCommitsSpec());
                specs.add(getGitBranchesSpec());
                specs.add(getGitRemotesSpec());
                specs.add(getGitDiffStatSpec());
                return specs;
        }

        public List<String> registeredToolNames() {
                return toolSpecifications().stream().map(ToolSpecification::name).toList();
        }

        public Map<String, Function<ToolExecutionRequest, String>> dispatchMap() {
                final Map<String, Function<ToolExecutionRequest, String>> dispatch = new LinkedHashMap<>();

                dispatch.put("listDirectoryContents",
                                request -> workspaceContextTools
                                                .listDirectoryContents(stringArg(request, "directoryPath")));

                dispatch.put("change_working_directory",
                                request -> workspaceContextTools
                                                .changeWorkingDirectory(stringArg(request, "directoryPath")));

                dispatch.put("list_files_recursive", request -> workspaceContextTools.listFilesRecursive(
                                stringArg(request, "directoryPath"),
                                integerArg(request, "maxResults")));

                dispatch.put("analyze_path_detailed",
                                request -> workspaceContextTools.analyzePathDetailed(stringArg(request, "path")));

                dispatch.put("summarize_path",
                                request -> workspaceContextTools.summarizePath(stringArg(request, "path")));

                dispatch.put("readFile", request -> readFileTools.readFile(stringArg(request, "path")));

                dispatch.put("searchWeb",
                                request -> webSearchTools.searchWeb(stringArg(request, "query"),
                                                integerArg(request, "maxResults")));

                dispatch.put("get_repository_status",
                                request -> gitMetadataTools.getRepositoryStatus(stringArg(request, "directoryPath")));

                dispatch.put("get_recent_commits",
                                request -> gitMetadataTools.getRecentCommits(
                                                stringArg(request, "directoryPath"),
                                                integerArg(request, "count")));

                dispatch.put("get_git_branches",
                                request -> gitMetadataTools.getGitBranches(stringArg(request, "directoryPath")));

                dispatch.put("get_git_remotes",
                                request -> gitMetadataTools.getGitRemotes(stringArg(request, "directoryPath")));

                dispatch.put("get_git_diff_stat",
                                request -> gitMetadataTools.getGitDiffStat(stringArg(request, "directoryPath")));

                return dispatch;
        }

        public String execute(final ToolExecutionRequest request) {
                final Function<ToolExecutionRequest, String> executor = dispatchMap().get(request.name());
                if (executor == null) {
                        return "Error: unknown tool: " + request.name();
                }
                try {
                        return executor.apply(request);
                } catch (final Exception e) {
                        return "Error executing tool " + request.name() + ": " + e.getMessage();
                }
        }

        private ToolSpecification listDirectoryContentsSpec() {
                return ToolSpecification.builder()
                                .name("listDirectoryContents")
                                .description(
                                                "Lists immediate (non-recursive) children of a directory as [F] file and [D] directory entries.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("directoryPath", JsonStringSchema.builder()
                                                                .description(
                                                                                "Absolute or relative directory path. Blank means current working directory.")
                                                                .build())
                                                .build())
                                .build();
        }

        private ToolSpecification changeWorkingDirectorySpec() {
                return ToolSpecification.builder()
                                .name("change_working_directory")
                                .description("Changes server-side working directory to a path inside an open project root.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("directoryPath", JsonStringSchema.builder()
                                                                .description("Absolute or relative directory path to switch to.")
                                                                .build())
                                                .required("directoryPath")
                                                .build())
                                .build();
        }

        private ToolSpecification listFilesRecursiveSpec() {
                return ToolSpecification.builder()
                                .name("list_files_recursive")
                                .description("Recursively lists regular files under a directory, up to a max limit.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("directoryPath", JsonStringSchema.builder()
                                                                .description(
                                                                                "Absolute or relative directory path. Blank means current working directory.")
                                                                .build())
                                                .addIntegerProperty("maxResults")
                                                .build())
                                .build();
        }

        private ToolSpecification analyzePathDetailedSpec() {
                return ToolSpecification.builder()
                                .name("analyze_path_detailed")
                                .description("Returns detailed file/directory metadata and preview information.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("path", JsonStringSchema.builder()
                                                                .description("Absolute or relative path. Blank means current working directory.")
                                                                .build())
                                                .build())
                                .build();
        }

        private ToolSpecification summarizePathSpec() {
                return ToolSpecification.builder()
                                .name("summarize_path")
                                .description("Returns a concise summary for a file or directory.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("path", JsonStringSchema.builder()
                                                                .description("Absolute or relative path. Blank means current working directory.")
                                                                .build())
                                                .build())
                                .build();
        }

        private ToolSpecification readFileSpec() {
                return ToolSpecification.builder()
                                .name("readFile")
                                .description("Reads and returns full raw text content of a file path.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("path", JsonStringSchema.builder()
                                                                .description("Absolute or relative file path within open project roots.")
                                                                .build())
                                                .required("path")
                                                .build())
                                .build();
        }

        private ToolSpecification searchWebSpec() {
                return ToolSpecification.builder()
                                .name("searchWeb")
                                .description("Runs a live web search and returns top title+URL results.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("query", JsonStringSchema.builder()
                                                                .description("Search query text.")
                                                                .build())
                                                .addIntegerProperty("maxResults")
                                                .required("query")
                                                .build())
                                .build();
        }

        private ToolSpecification getRepositoryStatusSpec() {
                return ToolSpecification.builder()
                                .name("get_repository_status")
                                .description("Returns the short git status for a repository directory.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("directoryPath", JsonStringSchema.builder()
                                                                .description(
                                                                                "Absolute or relative repository directory. Blank means current working directory.")
                                                                .build())
                                                .build())
                                .build();
        }

        private ToolSpecification getRecentCommitsSpec() {
                return ToolSpecification.builder()
                                .name("get_recent_commits")
                                .description("Returns recent git commits with hash, date, author, and subject.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("directoryPath", JsonStringSchema.builder()
                                                                .description(
                                                                                "Absolute or relative repository directory. Blank means current working directory.")
                                                                .build())
                                                .addIntegerProperty("count")
                                                .build())
                                .build();
        }

        private ToolSpecification getGitBranchesSpec() {
                return ToolSpecification.builder()
                                .name("get_git_branches")
                                .description("Returns local and remote git branches for a repository directory.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("directoryPath", JsonStringSchema.builder()
                                                                .description(
                                                                                "Absolute or relative repository directory. Blank means current working directory.")
                                                                .build())
                                                .build())
                                .build();
        }

        private ToolSpecification getGitRemotesSpec() {
                return ToolSpecification.builder()
                                .name("get_git_remotes")
                                .description("Returns configured git remotes and URLs for a repository directory.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("directoryPath", JsonStringSchema.builder()
                                                                .description(
                                                                                "Absolute or relative repository directory. Blank means current working directory.")
                                                                .build())
                                                .build())
                                .build();
        }

        private ToolSpecification getGitDiffStatSpec() {
                return ToolSpecification.builder()
                                .name("get_git_diff_stat")
                                .description("Returns a diffstat summary of uncommitted changes in a repository directory.")
                                .parameters(JsonObjectSchema.builder()
                                                .addProperty("directoryPath", JsonStringSchema.builder()
                                                                .description(
                                                                                "Absolute or relative repository directory. Blank means current working directory.")
                                                                .build())
                                                .build())
                                .build();
        }

        private String stringArg(final ToolExecutionRequest request, final String key) {
                final JsonNode node = parsedArguments(request).get(key);
                if (node == null || node.isNull()) {
                        return null;
                }
                return node.asText();
        }

        private Integer integerArg(final ToolExecutionRequest request, final String key) {
                final JsonNode node = parsedArguments(request).get(key);
                if (node == null || node.isNull()) {
                        return null;
                }
                if (node.isInt()) {
                        return node.asInt();
                }
                try {
                        return Integer.valueOf(node.asText());
                } catch (final Exception ignored) {
                        return null;
                }
        }

        private JsonNode parsedArguments(final ToolExecutionRequest request) {
                try {
                        if (request.arguments() == null || request.arguments().isBlank()) {
                                return objectMapper.createObjectNode();
                        }
                        return objectMapper.readTree(request.arguments());
                } catch (final Exception ignored) {
                        return objectMapper.createObjectNode();
                }
        }
}