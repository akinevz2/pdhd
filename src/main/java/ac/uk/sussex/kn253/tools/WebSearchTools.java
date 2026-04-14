package ac.uk.sussex.kn253.tools;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ac.uk.sussex.kn253.services.TelemetryService;
import ac.uk.sussex.kn253.support.BackendSupport;
import ac.uk.sussex.kn253.support.ToolSupport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WebSearchTools {

    static final String SEARCH_BASE_URL_PROPERTY = BackendSupport.WEB_SEARCH_BASE_URL_PROPERTY;
    static final String DEFAULT_SEARCH_BASE_URL = BackendSupport.DUCKDUCKGO_LITE_BASE_URL;

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "<a[^>]*href=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Inject
    TelemetryService telemetryService;

    @Tool("Run a web search and return top results (title + URL).")
    public String searchWeb(
            @P("Search query") final String query,
            @P("Maximum number of results to return (1-10)") final Integer maxResults) {
        final Instant started = Instant.now();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            if (query == null || query.isBlank()) {
                argumentValidationFailure = true;
                result = "Error: query must not be blank";
                return result;
            }

            final int limit = clampResultLimit(maxResults);
            final String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);

            final String html = fetch(buildSearchUrl(encodedQuery));
            final List<SearchItem> items = parseResults(html, limit);

            if (items.isEmpty()) {
                result = "No results found for: " + query;
                return result;
            }

            final StringBuilder out = new StringBuilder();
            out.append("Top results for: ").append(query).append("\n");
            for (int i = 0; i < items.size(); i++) {
                final SearchItem item = items.get(i);
                out.append(i + 1)
                        .append(". ")
                        .append(item.title())
                        .append("\n   ")
                        .append(item.url())
                        .append("\n");
            }
            result = out.toString().trim();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Web search failed: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "searchWeb",
                        ToolSupport.MODULE_WEB_SEARCH,
                        query,
                        result,
                        Math.max(0L, Duration.between(started, Instant.now()).toNanos()),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    private int clampResultLimit(final Integer maxResults) {
        if (maxResults == null) {
            return 5;
        }
        return Math.max(1, Math.min(10, maxResults));
    }

    String buildSearchUrl(final String encodedQuery) {
        final String configured = System.getProperty(SEARCH_BASE_URL_PROPERTY);
        final String baseUrl = (configured == null || configured.isBlank())
                ? DEFAULT_SEARCH_BASE_URL
                : configured.trim();

        final String separator = baseUrl.contains("?")
                ? (baseUrl.endsWith("?") || baseUrl.endsWith("&") ? "" : "&")
                : "?";
        return baseUrl + separator + "q=" + encodedQuery;
    }

    private String fetch(final String url) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "PDHD/1.0")
                .GET()
                .build();
        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body() != null ? response.body() : "";
    }

    private List<SearchItem> parseResults(final String html, final int maxResults) {
        final Matcher matcher = LINK_PATTERN.matcher(html);
        final Set<String> seenUrls = new LinkedHashSet<>();
        final List<SearchItem> items = new ArrayList<>();

        while (matcher.find() && items.size() < maxResults) {
            final String href = decodeDuckDuckGoRedirect(htmlUnescape(matcher.group(1)));
            final String title = stripTags(htmlUnescape(matcher.group(2))).trim();

            if (!isExternalHttpUrl(href) || title.isBlank()) {
                continue;
            }
            if (isSearchEngineSelfLink(href)) {
                continue;
            }
            if (!seenUrls.add(href)) {
                continue;
            }
            items.add(new SearchItem(title, href));
        }

        return items;
    }

    private boolean isExternalHttpUrl(final String url) {
        final String normalized = url == null ? "" : url.trim();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private boolean isSearchEngineSelfLink(final String url) {
        final String normalized = url == null ? "" : url.trim().toLowerCase();
        return normalized.startsWith("https://duckduckgo.com/")
                || normalized.startsWith("http://duckduckgo.com/")
                || normalized.startsWith("https://lite.duckduckgo.com/")
                || normalized.startsWith("http://lite.duckduckgo.com/");
    }

    private String decodeDuckDuckGoRedirect(final String url) {
        if (url == null) {
            return "";
        }
        final int idx = url.indexOf("uddg=");
        if (idx < 0) {
            return url;
        }
        final String encoded = url.substring(idx + 5);
        final int amp = encoded.indexOf('&');
        final String token = amp >= 0 ? encoded.substring(0, amp) : encoded;
        return URLDecoder.decode(token, StandardCharsets.UTF_8);
    }

    private String stripTags(final String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    }

    private String htmlUnescape(final String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private record SearchItem(String title, String url) {
    }
}
