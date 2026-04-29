package ac.uk.sussex.kn253.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ac.uk.sussex.kn253.AiToolCallException;
import ac.uk.sussex.kn253.ConversationalException;
import ac.uk.sussex.kn253.services.AnalysisService;

/**
 * Unit tests for {@link ToolDispatcher}.
 *
 * <p>
 * No CDI container. Stubs for {@link AnalysisService} and {@link ToolRegistry}
 * are wired via package-private fields. {@link ToolDefinition} instances are
 * constructed directly.
 */
class ToolDispatcherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolDispatcher dispatcher;
    private StubToolRegistry registry;
    private StubAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        registry = new StubToolRegistry();
        analysisService = new StubAnalysisService();

        dispatcher = new ToolDispatcher();
        dispatcher.toolRegistry = registry;
        dispatcher.analysisService = analysisService;
    }

    // ── newAccumulator ────────────────────────────────────────────────────────

    @Test
    void newAccumulatorReturnsEmptyMutableList() {
        final List<AiToolCallException> acc = dispatcher.newAccumulator();
        assertNotNull(acc);
        assertTrue(acc.isEmpty());
        acc.add(new AiToolCallException("test")); // must not throw
    }

    // ── parseToolCall — happy path ────────────────────────────────────────────

    @Test
    void parseToolCallReturnsToolCallForKnownTool() {
        registry.addKnown("readFile");
        final String raw = "{\"name\":\"readFile\",\"arguments\":{\"path\":\"/readme.md\"}}";
        final List<AiToolCallException> acc = dispatcher.newAccumulator();

        final ToolCall result = dispatcher.parseToolCall(raw, acc);

        assertNotNull(result);
        assertTrue(acc.isEmpty());
        assertEquals("readFile", result.name());
        assertEquals("/readme.md", result.arguments().get("path").asText());
    }

    @Test
    void parseToolCallCreatesEmptyArgsNodeWhenArgumentsIsAbsent() {
        registry.addKnown("summarize_path");
        final String raw = "{\"name\":\"summarize_path\"}";
        final List<AiToolCallException> acc = dispatcher.newAccumulator();

        final ToolCall result = dispatcher.parseToolCall(raw, acc);

        assertNotNull(result);
        assertTrue(acc.isEmpty());
        assertEquals("summarize_path", result.name());
        assertNotNull(result.arguments());
        assertEquals(0, result.arguments().size());
    }

    // ── parseToolCall — unknown tool ──────────────────────────────────────────

    @Test
    void parseToolCallAccruesErrorAndReturnsNullForUnknownTool() {
        final String raw = "{\"name\":\"nonExistentTool\",\"arguments\":{}}";
        final List<AiToolCallException> acc = dispatcher.newAccumulator();

        final ToolCall result = dispatcher.parseToolCall(raw, acc);

        assertNull(result);
        assertEquals(1, acc.size());
        assertTrue(acc.get(0).getMessage().contains("nonExistentTool"));
        assertTrue(acc.get(0).getMessage().startsWith(ToolDispatcher.MSG_UNKNOWN_TOOL));
    }

    // ── parseToolCall — malformed JSON ────────────────────────────────────────

    @Test
    void parseToolCallAccruesErrorForMalformedJson() {
        final String raw = "this is not json";
        final List<AiToolCallException> acc = dispatcher.newAccumulator();

        final ToolCall result = dispatcher.parseToolCall(raw, acc);

        assertNull(result);
        assertEquals(1, acc.size());
        assertEquals(ToolDispatcher.MSG_MALFORMED, acc.get(0).getMessage());
        assertEquals(raw, acc.get(0).getRawModelOutput());
    }

    @Test
    void parseToolCallAccruesErrorForEmptyString() {
        final List<AiToolCallException> acc = dispatcher.newAccumulator();

        final ToolCall result = dispatcher.parseToolCall("", acc);

        assertNull(result);
        assertEquals(1, acc.size());
    }

    @Test
    void parseToolCallAccruesErrorWhenNameFieldMissing() {
        // valid JSON but no "name" key — asText() on null node returns "" → unknown
        // tool
        registry.addKnown(""); // guard: even if "" were somehow looked up it'd be unknown path
        final String raw = "{\"arguments\":{}}";
        final List<AiToolCallException> acc = dispatcher.newAccumulator();

        final ToolCall result = dispatcher.parseToolCall(raw, acc);

        assertNull(result);
        assertEquals(1, acc.size());
    }

    // ── parseToolCall — accumulator guard ────────────────────────────────────

    @Test
    void parseToolCallThrowsOnNullAccumulator() {
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.parseToolCall("{}", null));
    }

    // ── validateSchema — valid call ───────────────────────────────────────────

    @Test
    void validateSchemaReturnsNullWhenAllKeysPresent() {
        final ObjectNode schema = MAPPER.createObjectNode();
        schema.put("path", "string");
        registry.addDefinition(new ToolDefinition("readFile", "reads a file", schema));

        final ObjectNode args = MAPPER.createObjectNode();
        args.put("path", "/readme.md");
        final ToolCall call = new ToolCall("readFile", args);

        final String corrective = dispatcher.validateSchema(call, 0, dispatcher.newAccumulator());

        assertNull(corrective);
    }

    @Test
    void validateSchemaReturnsNullForEmptySchemaAndEmptyArgs() {
        registry.addDefinition(new ToolDefinition(
                "summarize_path", "desc", MAPPER.createObjectNode()));

        final String corrective = dispatcher.validateSchema(
                new ToolCall("summarize_path", MAPPER.createObjectNode()),
                0,
                dispatcher.newAccumulator());

        assertNull(corrective);
    }

    // ── validateSchema — missing key ──────────────────────────────────────────

    @Test
    void validateSchemaReturnsCorrectiveMessageWhenKeyMissing() {
        final ObjectNode schema = MAPPER.createObjectNode();
        schema.put("path", "string");
        registry.addDefinition(new ToolDefinition("readFile", "reads a file", schema));

        // args has no "path" key
        final String corrective = dispatcher.validateSchema(
                new ToolCall("readFile", MAPPER.createObjectNode()),
                0,
                dispatcher.newAccumulator());

        assertNotNull(corrective);
        assertTrue(corrective.startsWith(ToolDispatcher.CORRECTIVE_PREFIX));
    }

    // ── validateSchema — extra key ────────────────────────────────────────────

    @Test
    void validateSchemaReturnsCorrectiveMessageWhenExtraKeyPresent() {
        registry.addDefinition(new ToolDefinition(
                "summarize_path", "desc", MAPPER.createObjectNode()));

        final ObjectNode args = MAPPER.createObjectNode();
        args.put("unexpectedKey", "value");

        final String corrective = dispatcher.validateSchema(
                new ToolCall("summarize_path", args),
                0,
                dispatcher.newAccumulator());

        assertNotNull(corrective);
        assertTrue(corrective.startsWith(ToolDispatcher.CORRECTIVE_PREFIX));
    }

    // ── validateSchema — retries exhausted ───────────────────────────────────

    @Test
    void validateSchemaAccruesErrorWhenRetriesExhausted() {
        final ObjectNode schema = MAPPER.createObjectNode();
        schema.put("path", "string");
        registry.addDefinition(new ToolDefinition("readFile", "reads a file", schema));

        final List<AiToolCallException> acc = dispatcher.newAccumulator();
        final String corrective = dispatcher.validateSchema(
                new ToolCall("readFile", MAPPER.createObjectNode()),
                ToolDispatcher.MAX_SCHEMA_RETRIES,
                acc);

        assertNull(corrective);
        assertEquals(1, acc.size());
        assertTrue(acc.get(0).getMessage().startsWith(ToolDispatcher.MSG_RETRIES_EXHAUSTED));
    }

    @Test
    void validateSchemaAllowsLastAttemptBeforeExhaustion() {
        final ObjectNode schema = MAPPER.createObjectNode();
        schema.put("path", "string");
        registry.addDefinition(new ToolDefinition("readFile", "reads a file", schema));

        final List<AiToolCallException> acc = dispatcher.newAccumulator();
        // retryCount = MAX - 1 is still allowed
        final String corrective = dispatcher.validateSchema(
                new ToolCall("readFile", MAPPER.createObjectNode()),
                ToolDispatcher.MAX_SCHEMA_RETRIES - 1,
                acc);

        assertNotNull(corrective);
        assertTrue(acc.isEmpty());
    }

    // ── dispatch — happy path ─────────────────────────────────────────────────

    @Test
    void dispatchReturnsResultFromAnalysisService() {
        final ObjectNode args = MAPPER.createObjectNode();
        args.put("path", "/readme.md");
        final ToolCall call = new ToolCall("readFile", args);
        analysisService.result = "file contents";

        final List<AiToolCallException> acc = dispatcher.newAccumulator();
        final String result = dispatcher.dispatch(call, acc);

        assertEquals("file contents", result);
        assertTrue(acc.isEmpty());
    }

    // ── dispatch — AiToolCallException from AnalysisService ──────────────────

    @Test
    void dispatchAccruesAiToolCallExceptionFromAnalysisService() {
        analysisService.throwAiToolCallException = true;
        final List<AiToolCallException> acc = dispatcher.newAccumulator();

        final String result = dispatcher.dispatch(
                new ToolCall("readFile", MAPPER.createObjectNode()), acc);

        assertNull(result);
        assertEquals(1, acc.size());
    }

    // ── dispatch — unexpected runtime exception ───────────────────────────────

    @Test
    void dispatchAccruesWrappedRuntimeExceptionFromAnalysisService() {
        analysisService.throwRuntimeException = true;
        final List<AiToolCallException> acc = dispatcher.newAccumulator();

        final String result = dispatcher.dispatch(
                new ToolCall("readFile", MAPPER.createObjectNode()), acc);

        assertNull(result);
        assertEquals(1, acc.size());
    }

    // ── buildConversationalException ──────────────────────────────────────────

    @Test
    void buildConversationalExceptionWrapsAllAccruedFailures() {
        final List<AiToolCallException> acc = dispatcher.newAccumulator();
        acc.add(new AiToolCallException("first failure"));
        acc.add(new AiToolCallException("second failure"));

        final ConversationalException ex = dispatcher.buildConversationalException(acc);

        assertNotNull(ex);
        assertEquals(2, ex.getCauses().size());
    }

    @Test
    void buildConversationalExceptionThrowsWhenAccumulatorEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.buildConversationalException(dispatcher.newAccumulator()));
    }

    // ── accumulator null-item guard ───────────────────────────────────────────

    @Test
    void dispatchThrowsOnNullAccumulator() {
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch(new ToolCall("readFile", MAPPER.createObjectNode()), null));
    }

    @Test
    void validateSchemaThrowsOnNullAccumulator() {
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.validateSchema(
                        new ToolCall("readFile", MAPPER.createObjectNode()), 0, null));
    }

    // =========================================================================
    // Stubs
    // =========================================================================

    private static final class StubToolRegistry extends ToolRegistry {

        private final java.util.Map<String, ToolDefinition> definitions = new java.util.LinkedHashMap<>();

        void addKnown(final String name) {
            definitions.put(name, new ToolDefinition(name, "stub", MAPPER.createObjectNode()));
        }

        void addDefinition(final ToolDefinition def) {
            definitions.put(def.name(), def);
        }

        @Override
        public boolean isKnownTool(final String name) {
            return definitions.containsKey(name);
        }

        @Override
        public ToolDefinition findByName(final String name) {
            return definitions.get(name);
        }

        @Override
        void initialise() {
            // no-op — not using CDI in unit tests
        }
    }

    private static final class StubAnalysisService extends AnalysisService {

        String result = "stub result";
        boolean throwAiToolCallException = false;
        boolean throwRuntimeException = false;

        @Override
        public String execute(final ac.uk.sussex.kn253.tools.ToolCall toolCall) {
            if (throwAiToolCallException) {
                throw new AiToolCallException("stub ai failure");
            }
            if (throwRuntimeException) {
                throw new RuntimeException("stub runtime failure");
            }
            return result;
        }
    }
}
