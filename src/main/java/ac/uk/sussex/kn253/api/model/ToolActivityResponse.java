package ac.uk.sussex.kn253.api.model;

import java.util.List;

/** Response body for {@code GET /api/tool-activity}. */
public record ToolActivityResponse(
                List<ToolActivityItem> items) {

        public record ToolActivityItem(
                        String timestamp,
                        String toolName,
                        String argumentsJson,
                        String result,
                        List<String> requestedFiles) {
        }
}
