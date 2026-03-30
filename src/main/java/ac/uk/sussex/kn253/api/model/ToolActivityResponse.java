package ac.uk.sussex.kn253.api.model;

import java.util.List;

import org.jspecify.annotations.NonNull;

import ac.uk.sussex.kn253.services.ToolActivityService.ToolActivityEvent;

/** Response body for {@code GET /api/tool-activity}. */
public record ToolActivityResponse(
                List<@NonNull ToolActivityItem> items) {

        public record ToolActivityItem(
                        String timestamp,
                        String toolName,
                        String argumentsJson,
                        String result,
                        List<String> requestedFiles) {

                public ToolActivityItem(final ToolActivityEvent event) {
                        this(event.timestamp(), event.toolName(), event.argumentsJson(), event.result(),
                                        event.requestedFiles());
                }
        }
}
