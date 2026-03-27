package ac.uk.sussex.kn253.services;

import java.util.List;

import ac.uk.sussex.kn253.services.tools.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ToolService {

    @Inject
    ExploreToolset exploreToolset;

    @Inject
    ReadToolset readToolset;

    @Inject
    WriteToolset writeToolset;

    @Inject
    IntrospectToolset introspectToolset;

    public List<ToolSpecification> toolSpecifications() {
        return java.util.stream.Stream.of(
                exploreToolset == null ? List.<ToolSpecification>of() : exploreToolset.toolSpecifications(),
                readToolset == null ? List.<ToolSpecification>of() : readToolset.toolSpecifications(),
                writeToolset == null ? List.<ToolSpecification>of() : writeToolset.toolSpecifications(),
                introspectToolset == null ? List.<ToolSpecification>of() : introspectToolset.toolSpecifications())
                .flatMap(List::stream)
                .toList();
    }

    public List<ToolSpecification> getTools() {
        return toolSpecifications();
    }

    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        if (exploreToolset != null && exploreToolset.canHandle(request.name())) {
            return exploreToolset.execute(request, memoryId);
        }
        if (readToolset != null && readToolset.canHandle(request.name())) {
            return readToolset.execute(request, memoryId);
        }
        if (writeToolset != null && writeToolset.canHandle(request.name())) {
            return writeToolset.execute(request, memoryId);
        }
        if (introspectToolset != null && introspectToolset.canHandle(request.name())) {
            return introspectToolset.execute(request, memoryId);
        }
        return "Unknown tool: " + request.name();
    }

}
