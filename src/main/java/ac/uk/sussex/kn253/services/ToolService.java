package ac.uk.sussex.kn253.services;

import java.util.List;

import ac.uk.sussex.kn253.services.tools.ExploreToolset;
import ac.uk.sussex.kn253.services.tools.ReadToolset;
import ac.uk.sussex.kn253.services.tools.WriteToolset;
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

    public List<ToolSpecification> toolSpecifications() {
        return java.util.stream.Stream.of(
                exploreToolset.toolSpecifications(),
                readToolset.toolSpecifications(),
                writeToolset.toolSpecifications())
                .flatMap(List::stream)
                .toList();
    }

    public List<ToolSpecification> getTools() {
        return toolSpecifications();
    }

    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        if (exploreToolset.canHandle(request.name())) {
            return exploreToolset.execute(request, memoryId);
        }
        if (readToolset.canHandle(request.name())) {
            return readToolset.execute(request, memoryId);
        }
        if (writeToolset.canHandle(request.name())) {
            return writeToolset.execute(request, memoryId);
        }
        return "Unknown tool: " + request.name();
    }

}
