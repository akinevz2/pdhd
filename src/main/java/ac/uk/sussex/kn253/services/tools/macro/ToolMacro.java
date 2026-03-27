package ac.uk.sussex.kn253.services.tools.macro;

import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;

public interface ToolMacro {

    ToolMacroDefinition definition();

    ToolSpecification specification();

    String execute(Map<String, Object> args, Object memoryId);
}