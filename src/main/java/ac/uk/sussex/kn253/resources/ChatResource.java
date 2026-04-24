package ac.uk.sussex.kn253.resources;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

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

@jakarta.ws.rs.Path("/api/chat")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class ChatResource {

    private static final Logger LOG = Logger.getLogger(ChatResource.class.getName());
    private static final String WEB_UI_MEMORY_ID = "webui-default";

    public record ChatRequest(String message) {
    }

    @Inject
    ProjectAssistant assistant;

    @Inject
    CwdService cwdService;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @org.jboss.resteasy.reactive.RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> stream(final ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BadRequestException("message must not be blank");
        }

        if (containsDisallowedAbsolutePath(request.message())) {
            return Multi.createFrom().item(
                    "Access denied: absolute paths outside currently open projects are not permitted.");
        }

        LOG.finest(() -> "Starting chat stream for message: " + request.message());
        return Multi.createFrom().emitter(emitter -> {
            try {
                final TokenStream tokenStream = assistant.stream(WEB_UI_MEMORY_ID, request.message());
                LOG.finest(() -> "TokenStream created, setting up callbacks");
                tokenStream
                        .onPartialResponse(chunk -> {
                            LOG.finest(() -> "Emitting chunk: " + chunk);
                            emitter.emit(chunk);
                        })
                        .onCompleteResponse(ignored -> {
                            LOG.finest("Stream completed");
                            emitter.complete();
                        })
                        .onError(error -> {
                            LOG.warning("Stream error: " + error.getMessage());
                            emitter.fail(error);
                        })
                        .start();
            } catch (final Exception e) {
                LOG.warning("Exception in stream setup: " + e.getMessage());
                emitter.fail(e);
            }
        });
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public java.util.Map<String, String> reset() {
        WebUiChatMemoryProviderSupplier.clear(WEB_UI_MEMORY_ID);
        return java.util.Map.of("status", "ok");
    }

    boolean containsDisallowedAbsolutePath(final String message) {
        // fixme: should not be implemented here, depend on CwdService
        final List<Path> allowedRoots = new ArrayList<>();
        allowedRoots.add(cwdService.getCurrentWorkingDirectory().toAbsolutePath().normalize());
        for (final String directory : cwdService.getOpenProjectDirectories()) {
            if (directory == null || directory.isBlank()) {
                continue;
            }
            allowedRoots.add(Path.of(directory).toAbsolutePath().normalize());
        }

        for (final String candidate : extractAbsolutePathCandidates(message)) {
            try {
                final Path normalized = Path.of(candidate).toAbsolutePath().normalize();
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
                final Path path = Path.of(token);
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
