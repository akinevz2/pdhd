package ac.uk.sussex.kn253.services.tools.macro;

import java.util.*;

import dev.langchain4j.agent.tool.ToolSpecification;

public class ToolMacroRegistry {

    private final List<ToolMacro> tools;
    private final Map<String, String> aliases;
    private final Map<String, ToolMacro> toolsByName;

    public ToolMacroRegistry(final List<? extends ToolMacro> tools) {
        this.tools = List.copyOf(tools);
        this.aliases = ToolMacros.aliasIndex(this.tools.stream().map(ToolMacro::definition).toList());
        this.toolsByName = new LinkedHashMap<>();
        for (final ToolMacro tool : this.tools) {
            this.toolsByName.put(tool.definition().name(), tool);
        }
    }

    public List<ToolSpecification> toolSpecifications() {
        return tools.stream().map(ToolMacro::specification).toList();
    }

    public boolean canHandle(final String toolName) {
        return toolsByName.containsKey(canonicalName(toolName));
    }

    public String execute(final String toolName, final Map<String, Object> args, final Object memoryId) {
        final ToolMacro tool = toolsByName.get(canonicalName(toolName));
        if (tool == null) {
            return "Unknown tool: " + toolName;
        }
        return tool.execute(args, memoryId);
    }

    public String canonicalName(final String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "";
        }
        return aliases.getOrDefault(toolName.trim().toLowerCase(java.util.Locale.ROOT), toolName);
    }
}