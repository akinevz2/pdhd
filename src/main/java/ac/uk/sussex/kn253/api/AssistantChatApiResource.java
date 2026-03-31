package ac.uk.sussex.kn253.api;

import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.api.model.*;
import ac.uk.sussex.kn253.services.ChatService;
import dev.langchain4j.exception.UnsupportedFeatureException;
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

    private static final Logger LOG = Logger.getLogger(AssistantChatApiResource.class);

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
        } catch (final UnsupportedFeatureException e) {
            LOG.warnf(e, "Model does not support requested chat features");
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (final Exception e) {
            LOG.errorf(e, "Chat with assistant failed");
            final String errorMessage = e.getMessage() == null ? "unknown error" : e.getMessage();
            final String frontendMessage = "Failed to chat with assistant: " + errorMessage;
            throw new WebApplicationException(frontendMessage, Response.Status.BAD_GATEWAY);
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
        } catch (final UnsupportedFeatureException e) {
            LOG.warnf(e, "Model does not support requested one-shot chat features");
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (final Exception e) {
            LOG.errorf(e, "One-shot message failed");
            final String errorMessage = e.getMessage() == null ? "unknown error" : e.getMessage();
            final String frontendMessage = "[Backend Exception] " + errorMessage;
            return new AssistantChatResponse(frontendMessage);
        }
    }

    @POST
    @Path("/summarize-folder")
    public AssistantChatResponse summarizeFolder(final FolderSummaryRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            throw new WebApplicationException("Path cannot be blank", Response.Status.BAD_REQUEST);
        }
        try {
            final String reply = chatService.summarizeDirectory(request.path().trim());
            return new AssistantChatResponse(reply == null ? "" : reply);
        } catch (final IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (final Exception e) {
            LOG.errorf(e, "Folder summarization failed for %s", request.path());
            final String errorMessage = e.getMessage() == null ? "unknown error" : e.getMessage();
            throw new WebApplicationException("Failed to summarize folder: " + errorMessage,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @POST
    @Path("/reset")
    public Response resetConversation() {
        chatService.resetConversation();
        return Response.noContent().build();
    }
}
