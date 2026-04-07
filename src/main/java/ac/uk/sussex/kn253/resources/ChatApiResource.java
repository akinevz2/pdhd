package ac.uk.sussex.kn253.resources;

import ac.uk.sussex.kn253.services.ChatService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * REST endpoints for chat interactions with the AI assistant.
 */
@Path("/api/chat")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatApiResource {

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String reply) {
    }

    @Inject
    ChatService chatService;

    @POST
    public Uni<ChatResponse> chat(final ChatRequest request) {
        if (request == null || request.message == null || request.message.isBlank()) {
            throw new WebApplicationException("Message is required", 400);
        }

        return chatService.chat(request.message)
                .collect()
                .asList()
                .map(chunks -> String.join("", chunks))
                .map(ChatResponse::new);
    }

    @POST
    @Path("/reset")
    public ChatResponse resetChat() {
        return new ChatResponse("Chat reset.");
    }

    @POST
    @Path("/summarize-folder")
    public Uni<ChatResponse> summarizeFolder(final ChatRequest request) {
        if (request == null || request.message == null || request.message.isBlank()) {
            throw new WebApplicationException("Message is required", 400);
        }

        return chatService.chat(request.message)
                .collect()
                .asList()
                .map(chunks -> String.join("", chunks))
                .map(ChatResponse::new);
    }
}
