package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ac.uk.sussex.kn253.AiToolCallException;
import ac.uk.sussex.kn253.AiToolsFailureException;
import ac.uk.sussex.kn253.tools.*;

/**
 * Unit tests for {@link AnalysisService}.
 *
 * <p>
 * The CDI container is not used. Each test wires {@code AnalysisService}
 * manually using package-private field access and calls
 * {@link AnalysisService#initialise()}
 * directly. Stub subclasses of the tool classes are injected so that dispatch
 * wiring can be verified independently of any I/O or database dependency.
 */
class AnalysisServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService();
        service.workspaceContextTools = new StubWorkspaceContextTools();
        service.readFileTools = new StubReadFileTools();
        service.webSearchTools = new StubWebSearchTools();
        service.gitMetadataTools = new StubGitMetadataTools();
        service.initialise();
    }

    // ── exact dispatch ────────────────────────────────────────────────────────

    @Test
    void listDirectoryContentsDispatchesToWorkspaceContextTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/projects");
        final String result = service.execute(new ToolCall("listDirectoryContents", args));
        assertEquals("listDirectoryContents:/projects", result);
    }

    @Test
    void changeWorkingDirectoryDispatchesToWorkspaceContextTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/home/user");
        final String result = service.execute(new ToolCall("change_working_directory", args));
        assertEquals("changeWorkingDirectory:/home/user", result);
    }

    @Test
    void listFilesRecursiveDispatchesToWorkspaceContextTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/src");
        args.put("maxResults", 50);
        final String result = service.execute(new ToolCall("list_files_recursive", args));
        assertEquals("listFilesRecursive:/src:50", result);
    }

    @Test
    void analyzePathDetailedDispatchesToWorkspaceContextTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("path", "/some/file.txt");
        final String result = service.execute(new ToolCall("analyze_path_detailed", args));
        assertEquals("analyzePathDetailed:/some/file.txt", result);
    }

    @Test
    void summarizePathDispatchesToWorkspaceContextTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("path", "/dir");
        final String result = service.execute(new ToolCall("summarize_path", args));
        assertEquals("summarizePath:/dir", result);
    }

    @Test
    void readFileDispatchesToReadFileTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("path", "/readme.md");
        final String result = service.execute(new ToolCall("readFile", args));
        assertEquals("readFile:/readme.md", result);
    }

    @Test
    void searchWebDispatchesToWebSearchTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("query", "quarkus cdi");
        args.put("maxResults", 3);
        final String result = service.execute(new ToolCall("searchWeb", args));
        assertEquals("searchWeb:quarkus cdi:3", result);
    }

    @Test
    void getRepositoryStatusDispatchesToGitMetadataTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/repo");
        final String result = service.execute(new ToolCall("get_repository_status", args));
        assertEquals("getRepositoryStatus:/repo", result);
    }

    @Test
    void getRecentCommitsDispatchesToGitMetadataTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/repo");
        args.put("count", 10);
        final String result = service.execute(new ToolCall("getRecentCommits", args));
        assertEquals("getRecentCommits:/repo:10", result);
    }

    @Test
    void getGitBranchesDispatchesToGitMetadataTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/repo");
        final String result = service.execute(new ToolCall("getGitBranches", args));
        assertEquals("getGitBranches:/repo", result);
    }

    @Test
    void getGitRemotesDispatchesToGitMetadataTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/repo");
        final String result = service.execute(new ToolCall("getGitRemotes", args));
        assertEquals("getGitRemotes:/repo", result);
    }

    @Test
    void getGitDiffStatDispatchesToGitMetadataTools() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/repo");
        final String result = service.execute(new ToolCall("getGitDiffStat", args));
        assertEquals("getGitDiffStat:/repo", result);
    }

    // ── null tool name ────────────────────────────────────────────────────────

    @Test
    void nullToolNameThrowsAiToolCallException() {
        final AiToolCallException ex = assertThrows(AiToolCallException.class,
                () -> service.execute(new ToolCall(null, MAPPER.createObjectNode())));
        assertEquals("AnalysisService received null tool name", ex.getMessage());
    }

    // ── unknown tool name ─────────────────────────────────────────────────────

    @Test
    void completelyUnknownToolNameThrowsAiToolCallException() {
        final AiToolCallException ex = assertThrows(AiToolCallException.class,
                () -> service.execute(new ToolCall("nonExistentTool", MAPPER.createObjectNode())));
        assertTrue(ex.getMessage().startsWith("AnalysisService received unknown tool name: "));
        assertTrue(ex.getMessage().contains("nonExistentTool"));
    }

    // ── prefix matching — unambiguous ─────────────────────────────────────────

    @Test
    void unambiguousPrefixResolvesAndExecutes() {
        // "getGitB" uniquely prefixes "getGitBranches"
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/repo");
        final String result = service.execute(new ToolCall("getGitB", args));
        assertEquals("getGitBranches:/repo", result);
    }

    @Test
    void unambiguousPrefixReadFileResolvesAndExecutes() {
        // "readF" uniquely prefixes "readFile"
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("path", "/file.txt");
        final String result = service.execute(new ToolCall("readF", args));
        assertEquals("readFile:/file.txt", result);
    }

    // ── prefix matching — ambiguous ───────────────────────────────────────────

    @Test
    void ambiguousPrefixThrowsAiToolCallException() {
        // "getGit" prefixes getGitBranches, getGitRemotes, getGitDiffStat
        final AiToolCallException ex = assertThrows(AiToolCallException.class,
                () -> service.execute(new ToolCall("getGit", MAPPER.createObjectNode())));
        assertTrue(ex.getMessage().startsWith("Ambiguous prefix match for tool name"));
        assertTrue(ex.getMessage().contains("getGit"));
    }

    // ── resolveMatches ────────────────────────────────────────────────────────

    @Test
    void resolveMatchesReturnsAllPrefixCandidates() {
        // "getGit" matches getGitBranches, getGitRemotes, getGitDiffStat
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/repo");
        final List<ToolCall> matches = service.resolveMatches(new ToolCall("getGit", args));
        assertEquals(3, matches.size());
        final List<String> names = matches.stream().map(ToolCall::name).toList();
        assertTrue(names.contains("getGitBranches"));
        assertTrue(names.contains("getGitRemotes"));
        assertTrue(names.contains("getGitDiffStat"));
    }

    @Test
    void resolveMatchesPreservesOriginalArguments() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/my/repo");
        final List<ToolCall> matches = service.resolveMatches(new ToolCall("getGitB", args));
        assertEquals(1, matches.size());
        assertEquals("/my/repo", matches.get(0).arguments().get("directoryPath").asText());
    }

    @Test
    void resolveMatchesReturnsEmptyListForNoMatch() {
        final List<ToolCall> matches = service.resolveMatches(
                new ToolCall("absolutelyNothing", MAPPER.createObjectNode()));
        assertTrue(matches.isEmpty());
    }

    @Test
    void resolveMatchesExactNameReturnsSingleEntry() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("path", "/x");
        final List<ToolCall> matches = service.resolveMatches(new ToolCall("readFile", args));
        assertEquals(1, matches.size());
        assertEquals("readFile", matches.get(0).name());
    }

    // ── null sentinel not returned by resolveMatches ──────────────────────────

    @Test
    void resolveMatchesDoesNotReturnNullSentinelKey() {
        // prefix "" would match all string keys but not null
        final List<ToolCall> matches = service.resolveMatches(new ToolCall("", MAPPER.createObjectNode()));
        assertTrue(matches.stream().noneMatch(tc -> tc.name() == null));
    }

    // ── tool implementation error ─────────────────────────────────────────────

    @Test
    void toolImplementationRuntimeExceptionWrappedInAiToolsFailureException() {
        service.readFileTools = new ExplodingReadFileTools();
        service.initialise();

        final ObjectNode args = MAPPER.createObjectNode();
        args.put("path", "/boom.txt");
        final AiToolsFailureException ex = assertThrows(AiToolsFailureException.class,
                () -> service.execute(new ToolCall("readFile", args)));
        assertEquals(AiToolsFailureException.FailureMode.TOOL_IMPLEMENTATION_ERROR, ex.getFirstFailureMode());
    }

    // ── argument extraction edge cases ────────────────────────────────────────

    @Test
    void nullJsonFieldValuePassedAsNullStringArg() {
        // directoryPath node is present but JSON null — stub receives null, returns
        // "listDirectoryContents:null"
        final ObjectNode args = MAPPER.createObjectNode();
        args.putNull("directoryPath");
        final String result = service.execute(new ToolCall("listDirectoryContents", args));
        assertEquals("listDirectoryContents:null", result);
    }

    @Test
    void missingArgFieldPassedAsNullStringArg() {
        // No directoryPath key at all — stub receives null
        final String result = service.execute(new ToolCall("listDirectoryContents", MAPPER.createObjectNode()));
        assertEquals("listDirectoryContents:null", result);
    }

    @Test
    void missingIntegerArgPassedAsNull() {
        // maxResults absent — integerArg returns null, listFilesRecursive stub shows
        // "null"
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("directoryPath", "/src");
        final String result = service.execute(new ToolCall("list_files_recursive", args));
        assertEquals("listFilesRecursive:/src:null", result);
    }

    // =========================================================================
    // Stub tool classes
    // =========================================================================

    /**
     * Returns predictable strings encoding the method name and key arguments
     * so dispatch wiring can be verified exactly.
     */
    private static final class StubWorkspaceContextTools extends WorkspaceContextTools {
        @Override
        public String listDirectoryContents(final String directoryPath) {
            return "listDirectoryContents:" + directoryPath;
        }

        @Override
        public String changeWorkingDirectory(final String directoryPath) {
            return "changeWorkingDirectory:" + directoryPath;
        }

        @Override
        public String listFilesRecursive(final String directoryPath, final Integer maxResults) {
            return "listFilesRecursive:" + directoryPath + ":" + maxResults;
        }

        @Override
        public String analyzePathDetailed(final String path) {
            return "analyzePathDetailed:" + path;
        }

        @Override
        public String summarizePath(final String path) {
            return "summarizePath:" + path;
        }
    }

    private static final class StubReadFileTools extends ReadFileTools {
        @Override
        public String readFile(final String path) {
            return "readFile:" + path;
        }
    }

    private static final class StubWebSearchTools extends WebSearchTools {
        @Override
        public String searchWeb(final String query, final Integer maxResults) {
            return "searchWeb:" + query + ":" + maxResults;
        }
    }

    private static final class StubGitMetadataTools extends GitMetadataTools {
        @Override
        public String getRepositoryStatus(final String directoryPath) {
            return "getRepositoryStatus:" + directoryPath;
        }

        @Override
        public String getRecentCommits(final String directoryPath, final Integer count) {
            return "getRecentCommits:" + directoryPath + ":" + count;
        }

        @Override
        public String getGitBranches(final String directoryPath) {
            return "getGitBranches:" + directoryPath;
        }

        @Override
        public String getGitRemotes(final String directoryPath) {
            return "getGitRemotes:" + directoryPath;
        }

        @Override
        public String getGitDiffStat(final String directoryPath) {
            return "getGitDiffStat:" + directoryPath;
        }
    }

    /** Stub that always throws to verify the implementation-error wrapping path. */
    private static final class ExplodingReadFileTools extends ReadFileTools {
        @Override
        public String readFile(final String path) {
            throw new IllegalStateException("simulated I/O failure");
        }
    }
}
