package ac.uk.sussex.kn253.services.tools;

import java.util.List;

import ac.uk.sussex.kn253.services.ProjectDiscoveryService;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacroToolset;
import ac.uk.sussex.kn253.services.tools.macro.explore.*;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Toolset that gives the AI assistant the ability to explore the file system
 * and interact with the working-directory state.
 */
@ApplicationScoped
public class ExploreToolset extends ToolMacroToolset {

    public ExploreToolset() {
        this(resolveWorkingDirectoryService(), null, null);
    }

    public ExploreToolset(final WorkingDirectoryService workingDirectoryService) {
        this(workingDirectoryService, null, null);
    }

    @Inject
    public ExploreToolset(
            final WorkingDirectoryService workingDirectoryService,
            final Instance<PathSummaryLlmService> pathSummaryLlmService,
            final Instance<ProjectDiscoveryService> projectDiscoveryService) {
        this(new ExploreToolSupport(workingDirectoryService, pathSummaryLlmService, projectDiscoveryService));
    }

    ExploreToolset(final ExploreToolSupport support) {
        super(List.of(
                new GetCurrentWorkingDirectoryTool(support),
                new ChangeWorkingDirectoryTool(support),
                new ResolvePathTool(support),
                new SearchPathsTool(support),
                new GetPathInfoTool(support),
                new ListSubdirectoriesTool(support),
                new ListFilesRecursiveTool(support),
                new AnalyzePathDetailedTool(support),
                new SummarizePathTool(support),
                new ListGitProjectsTool(support),
                new ListGithubProjectsTool(support),
                new ListProjectEntriesTool(support),
                new GetGitLogTool(support)));
    }

    private static boolean isCdiAvailable() {
        return Arc.container() != null && Arc.container().isRunning();
    }

    private static WorkingDirectoryService resolveWorkingDirectoryService() {
        if (isCdiAvailable()) {
            final var instance = Arc.container().instance(WorkingDirectoryService.class);
            if (instance != null && instance.isAvailable()) {
                return instance.get();
            }
        }
        return new WorkingDirectoryService();
    }
}
