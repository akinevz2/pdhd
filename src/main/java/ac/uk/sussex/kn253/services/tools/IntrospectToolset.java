package ac.uk.sussex.kn253.services.tools;

import java.util.List;

import ac.uk.sussex.kn253.services.*;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacroToolset;
import ac.uk.sussex.kn253.services.tools.macro.introspect.*;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class IntrospectToolset extends ToolMacroToolset {

    public IntrospectToolset() {
        this(resolveWorkingDirectoryService(), resolveToolActivityService(), resolveToolTelemetryService());
    }

    public IntrospectToolset(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService) {
        this(workingDirectoryService, toolActivityService, null);
    }

    @Inject
    public IntrospectToolset(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService,
            final ToolTelemetryService toolTelemetryService) {
        this(new IntrospectToolSupport(workingDirectoryService, toolActivityService, toolTelemetryService));
    }

    IntrospectToolset(final IntrospectToolSupport support) {
        super(List.of(
                new ReadFolderManifestTool(support),
                new ReadProjectManifestTool(support),
                new ReadProjectKnowledgeTool(support),
                new GetSessionContextTool(support),
                new OpenWorkspaceCanvasTool(support)));
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

    private static ToolActivityService resolveToolActivityService() {
        if (isCdiAvailable()) {
            final var instance = Arc.container().instance(ToolActivityService.class);
            if (instance != null && instance.isAvailable()) {
                return instance.get();
            }
        }
        return null;
    }

    private static ToolTelemetryService resolveToolTelemetryService() {
        if (isCdiAvailable()) {
            final var instance = Arc.container().instance(ToolTelemetryService.class);
            if (instance != null && instance.isAvailable()) {
                return instance.get();
            }
        }
        return null;
    }
}
