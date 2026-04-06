package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.events.CwdResolvedEvent;
import jakarta.enterprise.event.Event;
import jakarta.ws.rs.WebApplicationException;

class CwdServiceTest {

    @Test
    void setCurrentWorkingDirectoryFiresCwdResolvedEvent() throws Exception {
        final CwdService service = new CwdService();
        final List<CwdResolvedEvent> firedEvents = new ArrayList<>();
        service.cwdResolvedEvents = eventRecorder(firedEvents);

        final Path tempDir = Files.createTempDirectory("pdhd-cwd-event-");
        try {
            final String requested = tempDir.toString();
            final String resolved = service.setCurrentWorkingDirectory(requested);

            assertEquals(1, firedEvents.size(), "Exactly one CwdResolvedEvent should be fired");
            assertEquals(requested, firedEvents.get(0).requestedPath(), "Requested path should be preserved");
            assertEquals(resolved, firedEvents.get(0).resolvedPath(), "Resolved path should match returned cwd");
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void invalidDirectoryDoesNotFireCwdResolvedEvent() {
        final CwdService service = new CwdService();
        final List<CwdResolvedEvent> firedEvents = new ArrayList<>();
        service.cwdResolvedEvents = eventRecorder(firedEvents);

        final String missingPath = "/definitely/missing/path/for/pdhd/cwd-test";
        assertThrows(WebApplicationException.class, () -> service.setCurrentWorkingDirectory(missingPath));
        assertEquals(0, firedEvents.size(), "No CwdResolvedEvent should be fired on failure");
    }

    @SuppressWarnings("unchecked")
    private static Event<CwdResolvedEvent> eventRecorder(final List<CwdResolvedEvent> sink) {
        return (Event<CwdResolvedEvent>) Proxy.newProxyInstance(
                Event.class.getClassLoader(),
                new Class<?>[] { Event.class },
                (proxy, method, args) -> {
                    if ("fire".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof final CwdResolvedEvent event) {
                        sink.add(event);
                        return null;
                    }
                    if (method.getReturnType().isInstance(proxy)) {
                        return proxy;
                    }
                    return null;
                });
    }
}