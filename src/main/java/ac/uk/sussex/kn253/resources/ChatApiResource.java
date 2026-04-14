package ac.uk.sussex.kn253.resources;

import ac.uk.sussex.kn253.services.ai.ProjectAssistant;
import ac.uk.sussex.kn253.services.ai.WebUiChatMemoryProviderSupplier;
import dev.langchain4j.service.TokenStream;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/chat")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
public class ChatApiResource {

    private static final String WEB_UI_MEMORY_ID = "webui-default";

    public record ChatRequest(String message) {
    }

    @Inject
    ProjectAssistant assistant;

    @POST
    @Path("/stream")
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public Multi<String> streamMessage(final ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new WebApplicationException("message must not be blank", 400);
        }
        return Multi.createFrom().emitter(emitter -> {
            final TokenStream stream = assistant.stream(WEB_UI_MEMORY_ID, request.message());
            stream
                    .onPartialResponse(emitter::emit)
                    .onCompleteResponse(ignored -> emitter.complete())
                    .onError(emitter::fail)
                    .start();
        });
    }

    @POST
    @Path("/reset")
    public void resetConversation() {
        WebUiChatMemoryProviderSupplier.clear(WEB_UI_MEMORY_ID);
    }
}
