package ac.uk.sussex.kn253.api;

import ac.uk.sussex.kn253.api.model.AssistantChatRequest;
import ac.uk.sussex.kn253.api.model.AssistantChatResponse;
import ac.uk.sussex.kn253.services.ChatService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/chat")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AssistantChatApiResource {

    @Inject
    ChatService chatService;

    @POST
    public AssistantChatResponse chat(final AssistantChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new WebApplicationException("Message cannot be blank", Response.Status.BAD_REQUEST);
        }
        try {
            final String reply = chatService.sendMessage(request.message().trim());
            return new AssistantChatResponse(reply == null ? "" : reply);
        } catch (final Exception e) {
            throw new WebApplicationException("Failed to chat with assistant", Response.Status.BAD_GATEWAY);
        }
    }

    @POST
    @Path("/oneshot")
    public AssistantChatResponse oneShotChat(final AssistantChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new WebApplicationException("Message cannot be blank", Response.Status.BAD_REQUEST);
        }
        try {
            final String reply = chatService.sendOneShotMessage(request.message().trim());
            // If the reply is a known tool loop or max rounds error, surface it in the
            // response
            if (reply != null && (reply.startsWith("Tool execution exceeded maximum rounds")
                    || reply.startsWith("Stopped a repeated tool-call loop"))) {
                return new AssistantChatResponse("[Agent Error] " + reply);
            }
            return new AssistantChatResponse(reply == null ? "" : reply);
        } catch (final Exception e) {
            return new AssistantChatResponse("[Backend Exception] " + e.getMessage());
        }
    }

    @POST
    @Path("/reset")
    public Response resetConversation() {
        chatService.resetConversation();
        return Response.noContent().build();
    }
}
