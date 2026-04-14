package ac.uk.sussex.kn253.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.*;

import com.sun.net.httpserver.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class WebSearchToolsIntegrationTest {

    @Inject
    WebSearchTools webSearchTools;

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/lite/", new SearchPageHandler());
        server.start();

        final String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/lite/";
        System.setProperty(WebSearchTools.SEARCH_BASE_URL_PROPERTY, baseUrl);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(WebSearchTools.SEARCH_BASE_URL_PROPERTY);
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchWebReturnsParsedTopResults() {
        final String output = webSearchTools.searchWeb("pdhd test", 3);

        assertTrue(output.contains("Top results for: pdhd test"));
        assertTrue(output.contains("1. PDHD Result A"));
        assertTrue(output.contains("https://example.org/a"));
        assertTrue(output.contains("2. PDHD Result B"));
        assertTrue(output.contains("https://example.org/b"));
        assertFalse(output.contains("DuckDuckGo"));
    }

    @Test
    void searchWebDeduplicatesAndFiltersNonHttpLinks() {
        final String output = webSearchTools.searchWeb("duplicates", 10);

        // Duplicate URL should only appear once.
        final int first = output.indexOf("https://example.org/a");
        final int second = output.indexOf("https://example.org/a", first + 1);
        assertTrue(first >= 0);
        assertTrue(second < 0);

        // Non-http links should be ignored.
        assertFalse(output.contains("/internal/path"));
        assertFalse(output.contains("mailto:"));
    }

    @Test
    void searchWebRejectsBlankQuery() {
        final String output = webSearchTools.searchWeb("   ", 5);
        assertTrue(output.contains("query must not be blank"));
    }

    private static final class SearchPageHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            final String query = URLDecoder.decode(
                    parseQuery(exchange.getRequestURI()).getOrDefault("q", ""),
                    StandardCharsets.UTF_8);

            final String body;
            if ("duplicates".equalsIgnoreCase(query.trim())) {
                body = """
                        <html><body>
                          <a href="https://example.org/a">PDHD Result A</a>
                          <a href="https://example.org/a">PDHD Result A Duplicate</a>
                          <a href="/internal/path">Internal</a>
                          <a href="mailto:test@example.org">Mail</a>
                        </body></html>
                        """;
            } else {
                body = """
                        <html><body>
                          <a href="https://example.org/a">PDHD Result A</a>
                          <a href="https://example.org/b">PDHD Result B</a>
                          <a href="https://duckduckgo.com/about">DuckDuckGo</a>
                        </body></html>
                        """;
            }

            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private Map<String, String> parseQuery(final URI uri) {
            final String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isBlank()) {
                return Map.of();
            }

            final java.util.HashMap<String, String> values = new java.util.HashMap<>();
            for (final String pair : rawQuery.split("&")) {
                final int idx = pair.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                final String key = pair.substring(0, idx);
                final String value = pair.substring(idx + 1);
                values.put(key, value);
            }
            return values;
        }
    }
}
