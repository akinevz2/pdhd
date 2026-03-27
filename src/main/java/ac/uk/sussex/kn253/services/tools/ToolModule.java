package ac.uk.sussex.kn253.services.tools;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

public interface ToolModule {

    List<ToolSpecification> toolSpecifications();

    boolean canHandle(String toolName);

    String execute(ToolExecutionRequest request, Object memoryId);
}