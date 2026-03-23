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
    private static final Pattern REQUEST_PATH = Pattern
            .compile("\"(filePath|relativePath|path|projectDirectory)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RESULT_PATH = Pattern.compile("(?m)^path=(.+)$");

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
        final Set<String> files = new LinkedHashSet<>();

        if (argumentsJson != null && !argumentsJson.isBlank()) {
            try {
                final JsonNode node = MAPPER.readTree(argumentsJson);
                addJsonPath(node, "filePath", files);
                addJsonPath(node, "relativePath", files);
                addJsonPath(node, "path", files);
                addJsonPath(node, "projectDirectory", files);
            } catch (final Exception ignored) {
                final Matcher matcher = REQUEST_PATH.matcher(argumentsJson);
                while (matcher.find()) {
                    files.add(matcher.group(2));
                }
            }
        }

        if (result != null && !result.isBlank()) {
            final Matcher pathLine = RESULT_PATH.matcher(result);
            while (pathLine.find()) {
                final String value = pathLine.group(1).trim();
                if (!value.isBlank()) {
                    files.add(value);
                }
            }
        }

        // For tree/list tools, parse returned entries and keep file-like names.
        if (("list_project_entries".equals(toolName)
                || "list_subdirectories".equals(toolName)
                || "list_files_recursive".equals(toolName)
                || "list_files_in_project".equals(toolName)
                || "list_folders".equals(toolName)
                || "list_folder".equals(toolName))
                && result != null
                && !result.isBlank()) {
            for (final String line : result.split("\\R")) {
                final String trimmed = line.trim();
                if (!trimmed.isEmpty()
                        && !trimmed.endsWith("/")
                        && !trimmed.startsWith("Failed")
                        && !trimmed.startsWith("path=")) {
                    files.add(trimmed);
                }
            }
        }

        return List.copyOf(files);
    }

    private void addJsonPath(final JsonNode node, final String key, final Set<String> files) {
        final JsonNode value = node.get(key);
        if (value != null && !value.asText().isBlank()) {
            files.add(value.asText());
        }
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
