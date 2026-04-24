package ac.uk.sussex.kn253.prompts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.tools.WebSearchTools;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prompt spec: "Search the web for {query} and return a summary of the
 * results."
 *
 * Covers: docs/spec/web-search.md
 *
 * Note: these tests exercise argument validation and error-path behaviour only.
 * Live network calls are not made in CI; actual DuckDuckGo responses are
 * verified by the integration test {@code WebSearchToolsIntegrationTest}.
 */
@QuarkusTest
class WebSearchPromptTest {

    @Inject
    WebSearchTools webSearchTools;

    // ── argument validation ───────────────────────────────────────────────────

    @Test
    void searchWeb_returnsErrorForBlankQuery() {
        final String result = webSearchTools.searchWeb("   ", 5);

        assertNotNull(result);
        assertTrue(result.startsWith("Error:"), result);
        assertTrue(result.contains("blank"), result);
    }

    @Test
    void searchWeb_returnsErrorForNullQuery() {
        final String result = webSearchTools.searchWeb(null, 5);

        assertNotNull(result);
        assertTrue(result.startsWith("Error:"), result);
    }

    // ── result-limit clamping ─────────────────────────────────────────────────

    @Test
    void searchWeb_doesNotThrowForMinimumResultCount() {
        // maxResults = 1 is within spec range (1–10); must not throw
        final String result = webSearchTools.searchWeb("java", 1);

        assertNotNull(result);
        // Either results or a network-unavailable error — never a validation error
        assertFalse(result.contains("blank"), result);
    }

    @Test
    void searchWeb_doesNotThrowForNullResultCount() {
        // null maxResults should default gracefully
        final String result = webSearchTools.searchWeb("java", null);

        assertNotNull(result);
        assertFalse(result.contains("blank"), result);
    }

    @Test
    void searchWeb_doesNotThrowForAboveMaxResultCount() {
        // maxResults > 10 should be clamped, not throw
        final String result = webSearchTools.searchWeb("java", 999);

        assertNotNull(result);
        assertFalse(result.contains("blank"), result);
    }
}
