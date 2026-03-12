package ac.uk.sussex.kn253.services;

import java.util.List;

import ac.uk.sussex.kn253.services.tools.ExploreTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ToolService {

    @Inject
    ExploreTool exploreTool;

    public List<ToolSpecification> all() {
        return List.of(exploreTool.spec);
    }

    public List<ToolSpecification> getTools() {
        return all();
    }

}
