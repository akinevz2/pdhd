package ac.uk.sussex.kn253.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.services.SummaryOrchestratorService;
import ac.uk.sussex.kn253.services.ai.ChatService;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.WebApplicationException;

class ChatApiResourceTest {

    @Test
    void chatRejectsBlankMessage() {
        final ChatApiResource resource = new ChatApiResource();
        resource.chatService = message -> Multi.createFrom().item(message);

        assertThrows(WebApplicationException.class,
                () -> resource.chat(new ChatApiResource.ChatRequest("   ")));
    }

    @Test
    void chatCollectsStreamingChunksIntoSingleReply() {
        final ChatApiResource resource = new ChatApiResource();
        resource.chatService = new ChatService() {
            @Override
            public Multi<String> chat(final String userMessage) {
                return Multi.createFrom().items("alpha", "-", userMessage.trim());
            }
        };

        final ChatApiResource.ChatResponse response = resource.chat(new ChatApiResource.ChatRequest("beta"))
                .await().indefinitely();

        assertEquals("alpha-beta", response.reply());
    }

    @Test
    void summarizeFolderTrimsPathBeforeDelegating() {
        final ChatApiResource resource = new ChatApiResource();
        resource.summaryOrchestratorService = new SummaryOrchestratorService() {
            @Override
            public SummaryResult summarize(final String rawPath) {
                return new SummaryResult("summary:" + rawPath, "folder", rawPath);
            }
        };

        final ChatApiResource.ChatResponse response = resource
                .summarizeFolder(new ChatApiResource.FolderSummaryRequest("  ./docs  "))
                .await().indefinitely();

        assertEquals("summary:./docs", response.reply());
    }

    @Test
    void projectNextStepsRejectsMissingPath() {
        final ChatApiResource resource = new ChatApiResource();

        assertThrows(WebApplicationException.class,
                () -> resource.projectNextSteps(new ChatApiResource.NextStepsRequest(" ")));
    }

    @Test
    void projectNextStepsTrimsPathBeforeDelegating() {
        final ChatApiResource resource = new ChatApiResource();
        resource.summaryOrchestratorService = new SummaryOrchestratorService() {
            @Override
            public SummaryResult nextSteps(final String rawPath) {
                return new SummaryResult("next:" + rawPath, "project", rawPath);
            }
        };

        final ChatApiResource.ChatResponse response = resource
                .projectNextSteps(new ChatApiResource.NextStepsRequest("  /tmp/project  "))
                .await().indefinitely();

        assertEquals("next:/tmp/project", response.reply());
    }
}