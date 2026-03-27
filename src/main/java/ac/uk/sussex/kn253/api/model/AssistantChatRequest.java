package ac.uk.sussex.kn253.api.model;

/** Request body for {@code POST /api/assistant/chat}. */
public record AssistantChatRequest(String message) {
}
