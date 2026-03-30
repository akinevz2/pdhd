package ac.uk.sussex.kn253.testsupport;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class OllamaTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OllamaTestSupport() {
    }

    public static String testBaseUrl() {
        return System.getenv().getOrDefault("OLLAMA_TEST_BASE_URL", "http://desktop-box26.local:11434");
    }

    public static String toolModelPreference() {
        return System.getenv().getOrDefault("OLLAMA_TEST_TOOL_MODEL", "glm-4.7-flash:latest");
    }

    public static List<String> toolModelMatrix() {
        final String raw = System.getenv().getOrDefault(
                "OLLAMA_TEST_TOOL_MODELS",
            "glm-4.7-flash:latest,llama3.2:latest");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public static boolean hasModel(final List<String> available, final String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        final String normalizedExpected = normalizeModelName(expected);
        return available.stream()
                .map(OllamaTestSupport::normalizeModelName)
                .anyMatch(name -> name.equals(normalizedExpected));
    }

    public static String resolveAvailableModelName(final List<String> available, final String expected) {
        if (expected == null || expected.isBlank()) {
            return null;
        }
        final String normalizedExpected = normalizeModelName(expected);
        return available.stream()
                .filter(name -> normalizeModelName(name).equals(normalizedExpected))
                .findFirst()
                .orElse(null);
    }

    public static boolean isReachable(final String baseUrl) {
        try {
            final HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/api/version"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (final Exception e) {
            return false;
        }
    }

    public static List<String> modelNames(final String baseUrl) {
        try {
            final HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/api/tags"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }

            final JsonNode root = MAPPER.readTree(response.body());
            final JsonNode models = root.get("models");
            if (models == null || !models.isArray()) {
                return List.of();
            }

            final List<String> names = new ArrayList<>();
            for (final JsonNode model : models) {
                final JsonNode nameNode = model.get("name");
                if (nameNode != null && !nameNode.asText().isBlank()) {
                    names.add(nameNode.asText());
                }
            }
            return names;
        } catch (final Exception e) {
            return List.of();
        }
    }

    private static String trimTrailingSlash(final String baseUrl) {
        return baseUrl.replaceAll("/+$", "");
    }

    private static String normalizeModelName(final String value) {
        final String lower = value.toLowerCase(Locale.ROOT).trim();
        return lower.endsWith(":latest") ? lower.substring(0, lower.length() - 7) : lower;
    }
}
