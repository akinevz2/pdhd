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
        final List<Class<?>> toolClasses = List.of(
                ReadFileTools.class,
                WorkspaceContextTools.class,
                WebSearchTools.class);
        final Map<String, String> seen = new LinkedHashMap<>();
        final List<String> conflicts = new ArrayList<>();

        for (final Class<?> toolClass : toolClasses) {
            for (final Method method : discoverToolMethods(toolClass)) {
                final Tool annotation = method.getAnnotation(Tool.class);
                if (annotation == null) {
                    continue;
                }
                final String toolName = (annotation.name() != null && !annotation.name().isBlank())
                        ? annotation.name()
                        : method.getName();
                final String ownerClass = toolClass.getSimpleName();
                if (seen.containsKey(toolName)) {
                    conflicts.add(toolName + " (in " + seen.get(toolName) + " and " + ownerClass + ")");
                } else {
                    seen.put(toolName, ownerClass);
                }
            }
        }

        if (seen.isEmpty()) {
            throw new IllegalStateException(
                    "Tool discovery found 0 @Tool methods across registered modules; duplicate-name guard cannot run.");
        }

        if (!conflicts.isEmpty()) {
            throw new IllegalStateException(
                    "Duplicate tool names detected at startup – registration is ambiguous: " + conflicts);
        }
        LOG.infof("Tool dispatch check passed: %d unique tool names across %d modules",
                seen.size(), toolClasses.size());
    }

    private List<Method> discoverToolMethods(final Class<?> beanClass) {
        final List<Method> methods = new ArrayList<>();
        final Set<String> methodKeys = new LinkedHashSet<>();

        Class<?> current = beanClass;
        while (current != null && current != Object.class) {
            for (final Method method : current.getDeclaredMethods()) {
                final String key = buildMethodKey(method);
                if (methodKeys.add(key)) {
                    methods.add(method);
                }
            }
            current = current.getSuperclass();
        }

        return methods;
    }

    private String buildMethodKey(final Method method) {
        final StringBuilder key = new StringBuilder();
        key.append(method.getName()).append('(');
        final Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                key.append(',');
            }
            key.append(paramTypes[i].getName());
        }
        key.append(')');
        return key.toString();
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
