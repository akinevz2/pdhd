package ac.uk.sussex.kn253.api.model;

import java.util.List;

/**
 * Versioned response body for tool activity API evolution.
 *
 * <p>
 * Keeps stable schema metadata while preserving the existing event fields.
 */
public record VersionedToolActivityResponse(
        String schemaVersion,
        String generatedAt,
        String summary,
        List<ToolActivityItem> items) {

    public record ToolActivityItem(
            String timestamp,
            String toolName,
            String argumentsJson,
            String result,
            List<String> requestedFiles) {
    }
}
