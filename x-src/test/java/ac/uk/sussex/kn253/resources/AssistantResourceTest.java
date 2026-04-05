package ac.uk.sussex.kn253.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.services.AssistantService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class AssistantResourceTest {

    @Inject
    AssistantService assistantService;

    @Test
    void chatReturnsValidationErrorForBlankMessage() {
        final AssistantResource resource = buildResource();
        final String result = resource.chat("   ");

        assertEquals("Failed to chat with assistant: Message cannot be blank", result);
    }

    @Test
    void chatDelegatesToAssistantServiceForNonBlankMessageInQuarkusContext() {
        final AssistantResource resource = buildResource();
        final String result = resource.chat("hello");

        assertEquals("assistant reply: hello", result);
    }

    private AssistantResource buildResource() {
        final AssistantResource resource = new AssistantResource();
        resource.assistantService = assistantService;
        return resource;
    }
}