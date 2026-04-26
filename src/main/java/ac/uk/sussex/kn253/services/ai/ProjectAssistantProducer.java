package ac.uk.sussex.kn253.services.ai;

import java.util.*;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProjectAssistantProducer {

    @Inject
    AssistantToolRegistry toolRegistry;

    private static final Logger LOG = Logger.getLogger(ProjectAssistantProducer.class);

    /**
     * Startup guard: validates unique tool names from AssistantToolRegistry.
     */
    void validateToolNameUniqueness(@Observes final StartupEvent event) {
        final List<String> registeredNames = toolRegistry.registeredToolNames();
        final Set<String> seen = new LinkedHashSet<>();
        final List<String> conflicts = new ArrayList<>();

        for (final String toolName : registeredNames) {
            if (!seen.add(toolName)) {
                conflicts.add(toolName);
            }
        }

        if (registeredNames.isEmpty()) {
            throw new IllegalStateException(
                    "Tool discovery found 0 registered tools; duplicate-name guard cannot run.");
        }

        if (!conflicts.isEmpty()) {
            throw new IllegalStateException(
                    "Duplicate tool names detected at startup – registration is ambiguous: " + conflicts);
        }
        LOG.infof("Tool dispatch check passed: %d unique tool names", seen.size());
    }
}
