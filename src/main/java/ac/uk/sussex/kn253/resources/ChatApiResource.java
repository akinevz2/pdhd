package ac.uk.sussex.kn253.resources;

import java.util.ArrayList;
import java.util.List;

import ac.uk.sussex.kn253.services.CwdService;
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

    @Inject
    CwdService cwdService;

    @POST
    @Path("/stream")
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public Multi<String> streamMessage(final ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new WebApplicationException("message must not be blank", 400);
        }

        if (containsDisallowedAbsolutePath(request.message())) {
            return Multi.createFrom().item(
                    "Access denied: absolute paths outside currently open projects are not permitted.");
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

    boolean containsDisallowedAbsolutePath(final String message) {
        final List<java.nio.file.Path> allowedRoots = new ArrayList<>();
        allowedRoots.add(cwdService.getCurrentWorkingDirectory().toAbsolutePath().normalize());
        for (final String directory : cwdService.getOpenProjectDirectories()) {
            if (directory == null || directory.isBlank()) {
                continue;
            }
            allowedRoots.add(java.nio.file.Path.of(directory).toAbsolutePath().normalize());
        }

        for (final String candidate : extractAbsolutePathCandidates(message)) {
            try {
                final java.nio.file.Path normalized = java.nio.file.Path.of(candidate).toAbsolutePath().normalize();
                final boolean insideAllowedRoot = allowedRoots.stream().anyMatch(normalized::startsWith);
                if (!insideAllowedRoot) {
                    return true;
                }
            } catch (final Exception ignored) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractAbsolutePathCandidates(final String message) {
        final List<String> candidates = new ArrayList<>();
        final String[] tokens = message.split("\\s+");
        for (final String rawToken : tokens) {
            final String token = trimPathToken(rawToken);
            if (token.isBlank()) {
                continue;
            }
            try {
                final java.nio.file.Path path = java.nio.file.Path.of(token);
                if (!path.isAbsolute()) {
                    continue;
                }
                // Skip network-style paths such as //server/share; guard only local absolute
                // paths.
                if (token.startsWith("//")) {
                    continue;
                }
                candidates.add(token);
            } catch (final Exception ignored) {
                // Ignore non-path tokens.
            }
        }
        return candidates;
    }

    private String trimPathToken(final String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = rawToken.length() - 1;

        while (start <= end && isLeadingWrapper(rawToken.charAt(start))) {
            start++;
        }
        while (end >= start && isTrailingWrapper(rawToken.charAt(end))) {
            end--;
        }

        if (start > end) {
            return "";
        }
        return rawToken.substring(start, end + 1);
    }

    private boolean isLeadingWrapper(final char c) {
        return c == '"' || c == '\'' || c == '`' || c == '(' || c == '[' || c == '{' || c == '<';
    }

    private boolean isTrailingWrapper(final char c) {
        return c == '"' || c == '\'' || c == '`' || c == '.' || c == ',' || c == ';' || c == ':'
                || c == '!' || c == '?' || c == ')' || c == ']' || c == '}' || c == '>';
    }
}
