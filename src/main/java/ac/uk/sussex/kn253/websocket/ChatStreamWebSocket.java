package ac.uk.sussex.kn253.websocket;

import java.util.logging.Logger;

import ac.uk.sussex.kn253.services.ai.ChatService;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;

/**
 * WebSocket endpoint that streams assistant responses token-by-token.
 *
 * <p>
 * Path: {@code /ws/chat}
 *
 * <p>
 * <strong>Protocol (text frames):</strong><br>
 * Client sends a plain text user message. The server replies with a series of
 * JSON-encoded
 * {@link ChatStreamMessage} frames:
 * <ol>
 * <li>One or more {@code {"type":"token","content":"…"}} frames — incremental
 * response tokens.
 * <li>A single {@code {"type":"done","content":null}} frame — marks end of
 * stream.
 * <li>On failure, a {@code {"type":"error","content":"…"}} frame followed by
 * {@code done}.
 * </ol>
 *
 * <p>
 * The client may open multiple connections; each connection is independent and
 * maintains its
 * own LangChain4j conversation memory (managed by the
 * {@code @RegisterAiService} scope).
 */
@WebSocket(path = "/ws/chat")
public class ChatStreamWebSocket {

    private static final Logger LOG = Logger.getLogger(ChatStreamWebSocket.class.getName());

    @Inject
    ChatService chatService;

    /**
     * Handles an inbound user message and streams the assistant reply.
     *
     * <p>
     * Each token emitted by {@link ChatService#chat(String)} is wrapped in a
     * {@link ChatStreamMessage} of type {@code "token"} and transmitted
     * immediately. A
     * {@code "done"} message is appended via {@link Multi#onCompletion()} so the
     * client always
     * receives a deterministic end-of-stream signal regardless of whether the
     * response completed
     * normally or was recovered from an error.
     *
     * @param userMessage the raw text sent by the client (must not be blank).
     * @return a {@link Multi} of serialisable protocol messages; each element
     *         becomes one WebSocket
     *         text frame.
     */
    @OnTextMessage
    public Multi<ChatStreamMessage> onMessage(final String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Multi.createFrom().items(
                    ChatStreamMessage.error("Message must not be blank"),
                    ChatStreamMessage.done());
        }

        LOG.fine(() -> "WS chat: " + userMessage.substring(0, Math.min(80, userMessage.length())));

        return chatService.chat(userMessage.trim())
                .map(ChatStreamMessage::token)
                .onFailure().recoverWithItem(err -> {
                    LOG.warning(() -> "WS chat error: " + err.getMessage());
                    return ChatStreamMessage.error(err.getMessage());
                })
                .onCompletion().continueWith(ChatStreamMessage.done());
    }
}
