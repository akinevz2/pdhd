package ac.uk.sussex.kn253.services.tools.macro.explore;

import java.util.Map;

import ac.uk.sussex.kn253.services.tools.macro.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

public class ListGitProjectsTool implements ToolMacro {

    private final ExploreToolSupport support;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(ToolMacros.LIST_GIT_PROJECTS.name())
            .description("List known projects in the database that have a Git repository attached.")
            .parameters(JsonObjectSchema.builder().build())
            .build();

    public ListGitProjectsTool(final ExploreToolSupport support) {
        this.support = support;
    }

    @Override
    public ToolMacroDefinition definition() {
        return ToolMacros.LIST_GIT_PROJECTS;
    }

    @Override
    public ToolSpecification specification() {
        return specification;
    }

    @Override
    public String execute(final Map<String, Object> args, final Object memoryId) {
        return support.listGitProjects();
    }
}