package ac.uk.sussex.kn253.services.ai;

import java.util.ArrayList;
import java.util.List;

import ac.uk.sussex.kn253.tools.WorkspaceContextTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ImplicitContextBuilder {

    private static final String EMPTY_ARGUMENTS = "{}";

    @Inject
    WorkspaceContextTools workspaceContextTools;

    public List<ChatMessage> buildMessages() {
        final String cwd = workspaceContextTools.getCurrentWorkingDirectory();
        final List<String> openProjects = workspaceContextTools.getOpenProjectDirectories();

        final ToolExecutionRequest cwdRequest = ToolExecutionRequest.builder()
                .id("implicit-getCurrentWorkingDirectory")
                .name("getCurrentWorkingDirectory")
                .arguments(EMPTY_ARGUMENTS)
                .build();

        final ToolExecutionRequest projectsRequest = ToolExecutionRequest.builder()
                .id("implicit-getOpenProjectDirectories")
                .name("getOpenProjectDirectories")
                .arguments(EMPTY_ARGUMENTS)
                .build();

        final AiMessage syntheticAi = AiMessage.from(List.of(cwdRequest, projectsRequest));
        final ToolExecutionResultMessage cwdResult = ToolExecutionResultMessage.from(cwdRequest, cwd);
        final ToolExecutionResultMessage projectsResult = ToolExecutionResultMessage.from(projectsRequest,
                openProjects != null ? openProjects.toString() : "[]");

        final List<ChatMessage> messages = new ArrayList<>();
        messages.add(syntheticAi);
        messages.add(cwdResult);
        messages.add(projectsResult);
        return messages;
    }
}
