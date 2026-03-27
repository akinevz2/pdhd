package ac.uk.sussex.kn253.services;

import java.util.List;

import ac.uk.sussex.kn253.services.tools.ToolModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ToolService {

    @Inject
    Instance<ToolModule> toolModules;

    private List<ToolModule> testToolModules;

    ToolService() {
    }

    ToolService(final List<ToolModule> toolModules) {
        this.testToolModules = List.copyOf(toolModules);
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
        for (final ToolModule toolModule : toolModules()) {
            if (toolModule.canHandle(request.name())) {
                return toolModule.execute(request, memoryId);
            }
        }
        return "Unknown tool: " + request.name();
    }

    private List<ToolModule> toolModules() {
        if (testToolModules != null) {
            return testToolModules;
        }
        return toolModules == null ? List.of() : toolModules.stream().toList();
    }

}
