package ac.uk.sussex.kn253.services;

import java.util.*;

import ac.uk.sussex.kn253.services.tools.ToolModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ToolService {

    private static final List<String> MODULE_PRECEDENCE = List.of(
            "ac.uk.sussex.kn253.services.tools.ExploreToolset",
            "ac.uk.sussex.kn253.services.tools.ReadToolset",
            "ac.uk.sussex.kn253.services.tools.WriteToolset",
            "ac.uk.sussex.kn253.services.tools.IntrospectToolset");

    @Inject
    Instance<ToolModule> toolModules;

    @Inject
    ToolTelemetryService toolTelemetryService;

    private List<ToolModule> testToolModules;
    private volatile List<ToolModule> resolvedToolModules;
    private volatile boolean modulesValidated;

    ToolService() {
    }

    ToolService(final List<ToolModule> toolModules) {
        this(toolModules, null);
    }

    ToolService(final List<ToolModule> toolModules, final ToolTelemetryService toolTelemetryService) {
        this.testToolModules = List.copyOf(toolModules);
        this.toolTelemetryService = toolTelemetryService;
    }

    @PostConstruct
    void initialize() {
        toolModules();
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolModules().stream()
                .map(ToolModule::toolSpecifications)
                .flatMap(List::stream)
                .toList();
    }

    public List<ToolSpecification> getTools() {
        return toolSpecifications();
    }

    @Transactional
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        final String toolName = request == null ? "<null>" : request.name();
        final long startedAt = System.nanoTime();

        if (request == null || request.name() == null || request.name().isBlank()) {
            final String result = "Unknown tool: " + toolName;
            recordTelemetry(toolName, "unknown", startedAt, result, "UnknownTool");
            return result;
        }

        for (final ToolModule toolModule : toolModules()) {
            if (toolModule.canHandle(request.name())) {
                final String moduleName = toolModule.getClass().getSimpleName();
                try {
                    final String result = toolModule.execute(request, memoryId);
                    recordTelemetry(toolName, moduleName, startedAt, result, classifyError(result));
                    return result;
                } catch (final RuntimeException e) {
                    final String result = "Tool execution failed for " + request.name() + ": " + e.getMessage();
                    recordTelemetry(toolName, moduleName, startedAt, result, e.getClass().getSimpleName());
                    return result;
                }
            }
        }

        final String result = "Unknown tool: " + request.name();
        recordTelemetry(toolName, "unknown", startedAt, result, "UnknownTool");
        return result;
    }

    private List<ToolModule> toolModules() {
        if (modulesValidated && resolvedToolModules != null) {
            return resolvedToolModules;
        }

        synchronized (this) {
            if (modulesValidated && resolvedToolModules != null) {
                return resolvedToolModules;
            }

            final List<ToolModule> rawModules;
            if (testToolModules != null) {
                rawModules = testToolModules;
            } else {
                rawModules = toolModules == null ? List.of() : toolModules.stream().toList();
            }

            final List<ToolModule> orderedModules = rawModules.stream()
                    .sorted(Comparator
                            .comparingInt(this::precedenceRank)
                            .thenComparing(module -> module.getClass().getName()))
                    .toList();

            validateUniqueToolNames(orderedModules);
            resolvedToolModules = orderedModules;
            modulesValidated = true;
            return resolvedToolModules;
        }
    }

    private int precedenceRank(final ToolModule module) {
        final String className = module.getClass().getName();
        final int index = MODULE_PRECEDENCE.indexOf(className);
        return index >= 0 ? index : MODULE_PRECEDENCE.size() + 1;
    }

    private void validateUniqueToolNames(final List<ToolModule> modules) {
        final Map<String, List<String>> owners = new LinkedHashMap<>();

        for (final ToolModule module : modules) {
            final String moduleName = module.getClass().getSimpleName();
            for (final ToolSpecification specification : module.toolSpecifications()) {
                if (specification == null || specification.name() == null || specification.name().isBlank()) {
                    continue;
                }
                owners.computeIfAbsent(specification.name(), ignored -> new ArrayList<>())
                        .add(moduleName);
            }
        }

        final List<String> duplicates = owners.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " => " + String.join(", ", entry.getValue()))
                .toList();

        if (!duplicates.isEmpty()) {
            throw new IllegalStateException(
                    "Duplicate tool names detected across modules: " + String.join("; ", duplicates));
        }
    }

    private void recordTelemetry(
            final String toolName,
            final String moduleName,
            final long startedAtNanos,
            final String result,
            final String errorClass) {
        if (toolTelemetryService == null) {
            return;
        }
        final long durationNanos = Math.max(0L, System.nanoTime() - startedAtNanos);
        final boolean validationFailure = result != null && result.startsWith("Invalid tool arguments:");
        toolTelemetryService.record(
                toolName,
                moduleName,
                durationNanos,
                errorClass,
                validationFailure);
    }

    private String classifyError(final String result) {
        if (result == null) {
            return "NullResult";
        }
        if (result.startsWith("Unknown tool:")) {
            return "UnknownTool";
        }
        if (result.startsWith("Invalid tool arguments:")) {
            return "ArgumentValidation";
        }
        if (result.startsWith("Tool execution failed")) {
            return "ToolExecutionError";
        }
        if (result.startsWith("Failed ")) {
            return "ToolReportedFailure";
        }
        return null;
    }

}
