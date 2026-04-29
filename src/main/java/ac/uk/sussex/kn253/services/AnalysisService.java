package ac.uk.sussex.kn253.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ac.uk.sussex.kn253.AiToolCallException;
import ac.uk.sussex.kn253.AiToolsFailureException;
import ac.uk.sussex.kn253.tools.*;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Business-logic facade for the explicit tool dispatch path.
 *
 * <p>
 * {@code AnalysisService} receives fully parsed and validated {@link ToolCall}
 * instances from {@code ToolDispatcher} and executes them against the
 * appropriate tool class. It knows nothing about AI, model output, or prompt
 * construction.
 *
 * <h2>Boundary contract</h2>
 * <ul>
 * <li>All input arrives as a {@link ToolCall} — structurally valid, name
 * verified against the registry. Raw model output never reaches this class.
 * <li>All AI-layer failures (unknown tool name, malformed JSON, schema
 * mismatch) are represented as {@link AiToolCallException} and must be thrown
 * before this service is called.
 * <li>Runtime execution failures (I/O, network, business-rule rejection of a
 * valid argument value) may be represented as
 * {@link AiToolsFailureException} if structured accumulation is needed by the
 * caller.
 * </ul>
 *
 * <h2>Dispatch</h2>
 * <p>
 * A {@code Map<String, Function<ObjectNode, String>>} is built once at startup
 * in {@link #initialise()}. Each entry maps a tool name to a lambda that
 * extracts arguments from the {@link ObjectNode} and delegates to the
 * corresponding tool class. No switch statements, no ternary expressions.
 */
@ApplicationScoped
public class AnalysisService {

    private static final Logger LOG = Logger.getLogger(AnalysisService.class);

    private static final String KEY_DIRECTORY_PATH = "directoryPath";
    private static final String KEY_PATH = "path";
    private static final String KEY_QUERY = "query";
    private static final String KEY_MAX_RESULTS = "maxResults";
    private static final String KEY_COUNT = "count";

    private static final String UNKNOWN_TOOL_MSG = "AnalysisService received unknown tool name: ";
    private static final String NULL_TOOL_MSG = "AnalysisService received null tool name";
    private static final String AMBIGUOUS_PREFIX_MSG = "Ambiguous prefix match for tool name (use resolveMatches): ";

    @Inject
    WorkspaceContextTools workspaceContextTools;

    @Inject
    ReadFileTools readFileTools;

    @Inject
    WebSearchTools webSearchTools;

    @Inject
    GitMetadataTools gitMetadataTools;

    private Map<String, Function<ObjectNode, String>> dispatch;
    private PrefixMatcher prefixMatcher;

    /**
     * Builds the tool name → execution function dispatch map once at startup.
     * No entries reference AI layer types or raw model output.
     */
    @PostConstruct
    void initialise() {
        final Map<String, Function<ObjectNode, String>> map = new LinkedHashMap<>();

        // Null-key sentinel: short-circuits immediately when the tool name is null,
        // throwing AiToolCallException before any tool class is touched.
        // Named inner class (not a lambda) so a breakpoint can be set on the throw
        // line.
        map.put(null, new NullToolNameSentinel());

        map.put("listDirectoryContents",
                args -> workspaceContextTools.listDirectoryContents(stringArg(args, KEY_DIRECTORY_PATH)));

        map.put("change_working_directory",
                args -> workspaceContextTools.changeWorkingDirectory(stringArg(args, KEY_DIRECTORY_PATH)));

        map.put("list_files_recursive",
                args -> workspaceContextTools.listFilesRecursive(
                        stringArg(args, KEY_DIRECTORY_PATH),
                        integerArg(args, KEY_MAX_RESULTS)));

        map.put("analyze_path_detailed",
                args -> workspaceContextTools.analyzePathDetailed(stringArg(args, KEY_PATH)));

        map.put("summarize_path",
                args -> workspaceContextTools.summarizePath(stringArg(args, KEY_PATH)));

        map.put("readFile",
                args -> readFileTools.readFile(stringArg(args, KEY_PATH)));

        map.put("searchWeb",
                args -> webSearchTools.searchWeb(
                        stringArg(args, KEY_QUERY),
                        integerArg(args, KEY_MAX_RESULTS)));

        map.put("get_repository_status",
                args -> gitMetadataTools.getRepositoryStatus(stringArg(args, KEY_DIRECTORY_PATH)));

        map.put("getRecentCommits",
                args -> gitMetadataTools.getRecentCommits(
                        stringArg(args, KEY_DIRECTORY_PATH),
                        integerArg(args, KEY_COUNT)));

        map.put("getGitBranches",
                args -> gitMetadataTools.getGitBranches(stringArg(args, KEY_DIRECTORY_PATH)));

        map.put("getGitRemotes",
                args -> gitMetadataTools.getGitRemotes(stringArg(args, KEY_DIRECTORY_PATH)));

        map.put("getGitDiffStat",
                args -> gitMetadataTools.getGitDiffStat(stringArg(args, KEY_DIRECTORY_PATH)));

        dispatch = map;
        prefixMatcher = new PrefixMatcher(map.keySet());
        LOG.infof("AnalysisService initialised with %d tool executor(s)", dispatch.size());
    }

    /**
     * Executes the tool named by the supplied {@link ToolCall} and returns the
     * result string.
     *
     * <p>
     * Throws {@link AiToolCallException} if the tool name is not present in the
     * dispatch map — this should not occur in normal operation because
     * {@code ToolDispatcher} validates names against {@code ToolRegistry} before
     * calling this method, but is included as a defensive boundary guard.
     *
     * @param toolCall the parsed and validated tool-call request
     * @return the result string produced by the delegated tool class
     * @throws AiToolCallException if the tool name is unknown
     */
    /**
     * Resolves all registered tool keys whose name begins with the name in
     * {@code toolCall} (exact case, exact hyphenation) and returns one
     * {@link ToolCall} per match carrying the original arguments.
     *
     * <p>
     * The caller is responsible for narrowing the list to a single candidate
     * before passing it to {@link #execute(ToolCall)}. Providing an ambiguous
     * prefix directly to {@code execute} will throw {@link AiToolCallException}.
     *
     * @param toolCall the partially named tool-call request from the model
     * @return all registered keys that start with {@code toolCall.name()},
     *         each wrapped in a new {@link ToolCall} with the original arguments;
     *         empty list when no prefix match exists
     */
    public List<ToolCall> resolveMatches(final ToolCall toolCall) {
        final List<String> keys = prefixMatcher.find(toolCall.name());
        final List<ToolCall> result = new ArrayList<>(keys.size());
        for (final String key : keys) {
            result.add(new ToolCall(key, toolCall.arguments()));
        }
        return result;
    }

    public String execute(final ToolCall toolCall) {
        try {
            Function<ObjectNode, String> executor = dispatch.get(toolCall.name());
            if (executor == null) {
                final List<String> prefixMatches = prefixMatcher.find(toolCall.name());
                if (prefixMatches.isEmpty()) {
                    throw new AiToolCallException(UNKNOWN_TOOL_MSG + toolCall.name());
                }
                if (prefixMatches.size() > 1) {
                    throw new AiToolCallException(AMBIGUOUS_PREFIX_MSG + toolCall.name());
                }
                LOG.debugf("Prefix match: '%s' resolved to '%s'", toolCall.name(), prefixMatches.get(0));
                executor = dispatch.get(prefixMatches.get(0));
            }
            return executor.apply(toolCall.arguments());
        } catch (final AiToolCallException e) {
            throw e;
        } catch (final Exception e) {
            throw new AiToolsFailureException(
                    AiToolsFailureException.FailureMode.TOOL_IMPLEMENTATION_ERROR,
                    toolCall.name(),
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Functor that performs case-sensitive, exact-hyphenation prefix matching
     * against the registered dispatch key set. Constructed once after the
     * dispatch map is built and held as a member field.
     *
     * <p>
     * The null sentinel key is always skipped — it is not a valid prefix target.
     */
    private static final class PrefixMatcher {

        private final Iterable<String> keys;

        PrefixMatcher(final Iterable<String> keys) {
            this.keys = keys;
        }

        /**
         * Returns all keys that start with {@code prefix} (case-sensitive,
         * exact hyphenation). Short-circuits as soon as the first match is
         * found when only one result is needed — callers that only care about
         * uniqueness may check {@code size() > 1} to detect ambiguity.
         *
         * @param prefix the raw prefix string from the model output
         * @return ordered list of matching keys; never {@code null}
         */
        List<String> find(final String prefix) {
            final List<String> matches = new ArrayList<>();
            for (final String key : keys) {
                if (key != null && key.startsWith(prefix)) {
                    matches.add(key);
                }
            }
            return matches;
        }
    }

    /**
     * Sentinel executor registered under the {@code null} key in the dispatch map.
     * Invoked when {@link ToolCall#name()} is {@code null}, providing a named,
     * breakpoint-friendly anchor in the debugger before the exception is raised.
     */
    private static final class NullToolNameSentinel implements Function<ObjectNode, String> {
        @Override
        public String apply(final ObjectNode args) {
            throw new AiToolCallException(NULL_TOOL_MSG);
        }
    }

    private static String stringArg(final ObjectNode args, final String key) {
        final JsonNode node = args.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static Integer integerArg(final ObjectNode args, final String key) {
        final JsonNode node = args.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asInt(0);
    }
}
