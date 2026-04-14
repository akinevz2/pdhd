package ac.uk.sussex.kn253.services.ai;

import java.lang.reflect.Method;
import java.util.*;

import org.jboss.logging.Logger;

import ac.uk.sussex.kn253.tools.*;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProjectAssistantProducer {

    @Inject
    dev.langchain4j.model.chat.ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    ReadFileTools readFileTools;

    @Inject
    WorkspaceContextTools workspaceContextTools;

    @Inject
    WebSearchTools webSearchTools;

    private static final Logger LOG = Logger.getLogger(ProjectAssistantProducer.class);

    /**
     * Startup guard: collects every {@code @Tool}-annotated method name from the
     * registered tool beans and fails fast if any name appears more than once.
     *
     * <p>
     * This implements the duplicate-name check from recommendations §3. As new
     * tool classes are added to {@link #produceProjectAssistant()}, they must also
     * be listed here so the check remains exhaustive.
     */
    void validateToolNameUniqueness(@Observes final StartupEvent event) {
        final List<Object> toolBeans = List.of(readFileTools, workspaceContextTools, webSearchTools);
        final Map<String, String> seen = new LinkedHashMap<>();
        final List<String> conflicts = new ArrayList<>();

        for (final Object bean : toolBeans) {
            for (final Method method : bean.getClass().getMethods()) {
                final Tool annotation = method.getAnnotation(Tool.class);
                if (annotation == null) {
                    continue;
                }
                final String toolName = (annotation.name() != null && !annotation.name().isBlank())
                        ? annotation.name()
                        : method.getName();
                final String ownerClass = bean.getClass().getSimpleName();
                if (seen.containsKey(toolName)) {
                    conflicts.add(toolName + " (in " + seen.get(toolName) + " and " + ownerClass + ")");
                } else {
                    seen.put(toolName, ownerClass);
                }
            }
        }

        if (!conflicts.isEmpty()) {
            throw new IllegalStateException(
                    "Duplicate tool names detected at startup – registration is ambiguous: " + conflicts);
        }
        LOG.infof("Tool dispatch check passed: %d unique tool names across %d modules",
                seen.size(), toolBeans.size());
    }

    @Produces
    @ApplicationScoped
    public ProjectAssistant produceProjectAssistant() {
        return AiServices.builder(ProjectAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(new WebUiChatMemoryProviderSupplier().get())
                .tools(readFileTools, workspaceContextTools, webSearchTools)
                .build();
    }
}
