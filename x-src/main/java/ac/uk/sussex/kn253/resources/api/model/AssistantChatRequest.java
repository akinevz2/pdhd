package ac.uk.sussex.kn253.resources.api.model;

/** Request body for {@code POST /api/assistant/chat}. */
public record AssistantChatRequest(String message) {
}
