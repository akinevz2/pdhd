package ac.uk.sussex.kn253.services.tools.macro;

import java.util.List;
import java.util.Map;

import ac.uk.sussex.kn253.services.tools.ToolArguments;
import ac.uk.sussex.kn253.services.tools.ToolModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.*;

public abstract class ToolMacroToolset implements ToolProvider, ToolExecutor, ToolModule {

    private final ToolMacroRegistry registry;

    protected ToolMacroToolset(final List<? extends ToolMacro> tools) {
        this.registry = new ToolMacroRegistry(tools);
    }

    @Override
    public List<ToolSpecification> toolSpecifications() {
        return registry.toolSpecifications();
    }

    @Override
    public boolean canHandle(final String toolName) {
        return registry.canHandle(toolName);
    }

    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest request) {
        final ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (final ToolSpecification spec : toolSpecifications()) {
            builder.add(spec, this);
        }
        return builder.build();
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        try {
            final Map<String, Object> args = ToolArguments.parse(request.arguments());
            return registry.execute(request.name(), args, memoryId);
        } catch (final IllegalArgumentException e) {
            return "Invalid tool arguments: " + e.getMessage();
        } catch (final Exception e) {
            return "Tool execution failed for " + request.name() + ": " + e.getMessage();
        }
    }
}