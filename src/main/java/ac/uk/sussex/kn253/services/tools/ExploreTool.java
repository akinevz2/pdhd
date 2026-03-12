package ac.uk.sussex.kn253.services.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.*;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExploreTool implements ToolProvider, ToolExecutor {

    public final ToolSpecification spec;

    public ExploreTool() {
        super();
        final var builder = ToolSpecification.builder()
                .name("explore")
                .description("Explore a directory and list files and folders.");

        spec = builder.build();
    }

    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest arg0) {
        return ToolProviderResult.builder().add(spec, new ExploreTool()).build();
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        return "This is a stubbed response from the explore tool.";
    }

}
