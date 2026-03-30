package ac.uk.sussex.kn253.services.tools;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

public interface ToolModule {

    List<ToolSpecification> toolSpecifications();

    boolean canHandle(String toolName);

    String execute(ToolExecutionRequest request, Object memoryId);

    /**
     * Returns the operation category for a given tool name, used for telemetry
     * grouping and analytics. Defaults to the simple class name of the module.
     */
    default String operationCategoryFor(final String toolName) {
        return getClass().getSimpleName();
    }
}