package ac.uk.sussex.kn253.resources;

import ac.uk.sussex.kn253.services.SummaryOrchestratorService;
import ac.uk.sussex.kn253.services.ai.ChatService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * REST endpoints for assistant-driven inspection workflows.
 *
 * <p>
 * The chat endpoint handles general assistant interaction, while the dedicated
 * folder/project endpoints expose the application's higher-value workflows:
 * documenting local folders, documenting known projects, and estimating likely
 * next implementation steps from stored project evidence.
 */
@Path("/api/chat")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatApiResource {

    public record ChatRequest(String message) {
    }

    public record FolderSummaryRequest(String path) {
    }

    public record NextStepsRequest(String path) {
    }

    public record ChatResponse(String reply) {
    }

    @Inject
    ChatService chatService;

    @Inject
    SummaryOrchestratorService summaryOrchestratorService;

    @POST
    public Uni<ChatResponse> chat(final ChatRequest request) {
        final String message = requireMessage(request);

        return chatService.chat(message)
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
    public Uni<ChatResponse> summarizeFolder(final FolderSummaryRequest request) {
        final String path = requirePath(request == null ? null : request.path);
        return Uni.createFrom()
                .item(() -> summaryOrchestratorService.summarize(path))
                .map(result -> new ChatResponse(result.reply()));
    }

    @POST
    @Path("/project-next-steps")
    public Uni<ChatResponse> projectNextSteps(final NextStepsRequest request) {
        final String path = requirePath(request == null ? null : request.path);
        return Uni.createFrom()
                .item(() -> summaryOrchestratorService.nextSteps(path))
                .map(result -> new ChatResponse(result.reply()));
    }

    private String requireMessage(final ChatRequest request) {
        if (request == null || request.message == null || request.message.isBlank()) {
            throw new WebApplicationException("Message is required", 400);
        }
        return request.message.trim();
    }

    private String requirePath(final String path) {
        if (path == null || path.isBlank()) {
            throw new WebApplicationException("Path is required", 400);
        }
        return path.trim();
    }
}
