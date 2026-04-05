package ac.uk.sussex.kn253.resources;

import java.util.logging.Logger;

import ac.uk.sussex.kn253.services.AssistantService;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/chat")
@SessionScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AssistantResource {

    private static final Logger LOG = Logger.getLogger(AssistantResource.class.getName());
    private static final String ERROR_MESSAGE_BLANK = "Message cannot be blank";
    private static final String ERROR_PATH_BLANK = "Path cannot be blank";
    private static final String ERROR_UNKNOWN = "unknown error";
    private static final String LOG_CHAT_FEATURE_UNSUPPORTED = "Model does not support requested chat features";
    private static final String LOG_ONESHOT_FEATURE_UNSUPPORTED = "Model does not support requested one-shot chat features";
    private static final String LOG_CHAT_FAILED = "Chat with assistant failed";
    private static final String LOG_ONESHOT_FAILED = "One-shot message failed";
    private static final String LOG_SUMMARIZE_FAILED = "Folder summarization failed for %s";
    private static final String FRONTEND_CHAT_ERROR_PREFIX = "Failed to chat with assistant: ";
    private static final String FRONTEND_BACKEND_EXCEPTION_PREFIX = "[Backend Exception] ";
    private static final String FRONTEND_SUMMARY_ERROR_PREFIX = "Failed to summarize folder: ";
    private static final String EMPTY_RESPONSE = "";

    @Inject
    AssistantService assistantService;

    @POST
    @Path("/message")
    public String chat(final String message) {
        if (message == null || message.isBlank()) {
            return FRONTEND_CHAT_ERROR_PREFIX + ERROR_MESSAGE_BLANK;
        }
        return assistantService.chat(message);
    }
}
