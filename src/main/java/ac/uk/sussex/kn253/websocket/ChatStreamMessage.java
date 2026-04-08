package ac.uk.sussex.kn253.websocket;

/**
 * WebSocket protocol message for the streaming chat endpoint.
 *
 * <p>
 * Inbound (client → server): {@code type} is {@code "chat"}, {@code content}
 * holds the user's
 * message text.
 *
 * <p>
 * Outbound (server → client): {@code type} is one of:
 * <ul>
 * <li>{@code "token"} — a single streamed token, {@code content} holds the text
 * fragment.
 * <li>{@code "done"} — signals that the response stream has finished;
 * {@code content} is null.
 * <li>{@code "error"} — an error occurred; {@code content} describes the
 * failure.
 * </ul>
 */
public record ChatStreamMessage(String type, String content) {

    public static ChatStreamMessage token(final String fragment) {
        return new ChatStreamMessage("token", fragment);
    }

    public static ChatStreamMessage done() {
        return new ChatStreamMessage("done", null);
    }

    public static ChatStreamMessage error(final String detail) {
        return new ChatStreamMessage("error", detail);
    }
}
