package ac.uk.sussex.kn253.services;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ToolActivityService {

    private static final int MAX_EVENTS = 300;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern RELATIVE_FILE = Pattern.compile("\"(filePath|relativePath)\"\\s*:\\s*\"([^\"]+)\"");

    private final Deque<ToolActivityEvent> events = new ArrayDeque<>();

    public synchronized void record(final String toolName, final String argumentsJson, final String result) {
        final List<String> files = extractRequestedFiles(toolName, argumentsJson, result);
        events.addLast(new ToolActivityEvent(
                Instant.now().toString(),
                toolName,
                argumentsJson,
                truncate(result, 2000),
                files));

        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
    }

    public synchronized List<ToolActivityEvent> recent(final int limit) {
        final int safeLimit = Math.max(1, Math.min(limit, MAX_EVENTS));
        final List<ToolActivityEvent> all = new ArrayList<>(events);
        final int from = Math.max(0, all.size() - safeLimit);
        return all.subList(from, all.size());
    }

    private List<String> extractRequestedFiles(final String toolName, final String argumentsJson, final String result) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return List.of();
        }
        final Set<String> files = new LinkedHashSet<>();

        try {
            final JsonNode node = MAPPER.readTree(argumentsJson);
            final JsonNode filePath = node.get("filePath");
            if (filePath != null && !filePath.asText().isBlank()) {
                files.add(filePath.asText());
            }
            final JsonNode relativePath = node.get("relativePath");
            if (relativePath != null && !relativePath.asText().isBlank()) {
                files.add(relativePath.asText());
            }
        } catch (final Exception ignored) {
            final Matcher matcher = RELATIVE_FILE.matcher(argumentsJson);
            while (matcher.find()) {
                files.add(matcher.group(2));
            }
        }

        // For tree/list tools, parse returned entries and keep file-like names.
        if (("list_files_in_project".equals(toolName) || "list_folders".equals(toolName))
                && result != null
                && !result.isBlank()) {
            for (final String line : result.split("\\R")) {
                final String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.endsWith("/") && !trimmed.startsWith("Failed")) {
                    files.add(trimmed);
                }
            }
        }

        return List.copyOf(files);
    }

    private String truncate(final String value, final int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }

    public record ToolActivityEvent(
            String timestamp,
            String toolName,
            String argumentsJson,
            String result,
            List<String> requestedFiles) {
    }
}
