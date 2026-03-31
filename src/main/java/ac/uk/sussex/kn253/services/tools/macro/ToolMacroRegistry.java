package ac.uk.sussex.kn253.services.tools.macro;

import java.util.*;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

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
        validateRequiredArguments(tool, args == null ? Map.of() : args);
        return tool.execute(args, memoryId);
    }

    private void validateRequiredArguments(final ToolMacro tool, final Map<String, Object> args) {
        final ToolSpecification specification = tool.specification();
        if (specification == null) {
            return;
        }
        final JsonObjectSchema parameters = specification.parameters();
        if (parameters == null || parameters.required() == null || parameters.required().isEmpty()) {
            return;
        }

        final List<String> missing = parameters.required().stream()
                .filter(requiredKey -> {
                    if (!args.containsKey(requiredKey)) {
                        return true;
                    }
                    final Object value = args.get(requiredKey);
                    if (value == null) {
                        return true;
                    }
                    if (value instanceof final String strValue) {
                        return strValue.isBlank();
                    }
                    return false;
                })
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required argument(s) for " + tool.definition().name() + ": " + String.join(", ", missing));
        }
    }

    public String operationType(final String toolName) {
        final ToolMacro tool = toolsByName.get(canonicalName(toolName));
        return tool != null ? tool.definition().operationType().name() : "UNKNOWN";
    }

    public String canonicalName(final String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "";
        }
        return aliases.getOrDefault(toolName.trim().toLowerCase(java.util.Locale.ROOT), toolName);
    }
}