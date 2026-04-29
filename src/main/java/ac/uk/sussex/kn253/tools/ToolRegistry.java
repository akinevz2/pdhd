package ac.uk.sussex.kn253.tools;

import java.util.*;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Application-scoped registry for the explicit tool dispatch path.
 *
 * <p>
 * {@code ToolRegistry} collects every CDI bean that implements
 * {@link ToolContainer} at startup and builds an immutable list of
 * {@link ToolDefinition} instances. All definitions are constructed once during
 * {@link #initialise()} and are stable for the lifetime of the application.
 *
 * <p>
 * This class is distinct from {@code AssistantToolRegistry}, which serves the
 * LangChain4j implicit dispatch path. {@code ToolRegistry} is used exclusively
 * by {@code ToolDispatcher} for schema validation and explicit dispatch.
 *
 * <p>
 * At startup, the registry validates that no two registered tools share the
 * same name. A duplicate name indicates a programming error and causes the
 * application to fail fast via {@link AssertionError}.
 */
@ApplicationScoped
public class ToolRegistry {

    private static final Logger LOG = Logger.getLogger(ToolRegistry.class);
    private static final String DUPLICATE_TOOL_MSG = "CRITICAL: duplicate tool names detected in ToolRegistry: ";

    @Inject
    Instance<ToolContainer> containers;

    private List<ToolDefinition> definitions;
    private Set<String> knownNames;

    /**
     * Builds all {@link ToolDefinition} instances from the discovered
     * {@link ToolContainer} beans. Called once by the CDI container after
     * injection is complete.
     *
     * <p>
     * Fails with {@link AssertionError} if any two {@link ToolContainer} beans
     * return a {@link ToolDefinition} whose {@link Object#hashCode()} has already
     * been seen. Two definitions with identical name, description, and schema
     * produce the same hash; a collision therefore indicates a duplicate
     * registration and a wiring error in the CDI context.
     */
    @PostConstruct
    void initialise() {
        final List<ToolDefinition> built = new ArrayList<>();
        for (final ToolContainer container : containers) {
            built.add(container.schema());
        }

        final Set<Integer> seen = new HashSet<>();
        final List<String> duplicateNames = new ArrayList<>();
        for (final ToolDefinition def : built) {
            if (!seen.add(def.hashCode())) {
                duplicateNames.add(def.name());
            }
        }

        if (!duplicateNames.isEmpty()) {
            throw new AssertionError(DUPLICATE_TOOL_MSG + duplicateNames);
        }

        definitions = Collections.unmodifiableList(built);
        knownNames = Collections.unmodifiableSet(
                built.stream().map(ToolDefinition::name).collect(Collectors.toSet()));

        final List<String> names = built.stream().map(ToolDefinition::name).toList();
        LOG.infof("ToolRegistry initialised with %d tool(s): %s", definitions.size(), names);
    }

    /**
     * Returns an immutable list of all registered {@link ToolDefinition}
     * instances in the order they were discovered.
     *
     * @return unmodifiable list of tool definitions; never {@code null}
     */
    public List<ToolDefinition> allTools() {
        return definitions;
    }

    /**
     * Returns {@code true} if a tool with the given name is registered.
     *
     * <p>
     * Used by {@code ToolDispatcher.parseToolCall} to detect unknown tool names
     * in model output before attempting dispatch.
     *
     * @param name the tool name to look up; must not be {@code null}
     * @return {@code true} if a matching tool definition exists
     */
    public boolean isKnownTool(final String name) {
        return knownNames.contains(name);
    }

    /**
     * Returns the {@link ToolDefinition} for the given tool name, or
     * {@code null} if no tool with that name is registered.
     *
     * <p>
     * Callers should use {@link #isKnownTool(String)} first if they need to
     * distinguish between "unknown" and "found".
     *
     * @param name the tool name to look up; must not be {@code null}
     * @return the matching {@link ToolDefinition}, or {@code null}
     */
    public ToolDefinition findByName(final String name) {
        for (final ToolDefinition definition : definitions) {
            if (definition.name().equals(name)) {
                return definition;
            }
        }
        return null;
    }
}
