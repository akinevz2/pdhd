package ac.uk.sussex.kn253.services.tools;

import java.util.List;

import ac.uk.sussex.kn253.services.*;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacro;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacroToolset;
import ac.uk.sussex.kn253.services.tools.macro.introspect.*;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class IntrospectToolset extends ToolMacroToolset {

    public IntrospectToolset() {
        this(resolveWorkingDirectoryService(), resolveToolActivityService(), resolveToolTelemetryService(),
                resolveEmbeddingService());
    }

    public IntrospectToolset(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService) {
        this(workingDirectoryService, toolActivityService, null, null);
    }

    @Inject
    public IntrospectToolset(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService,
            final ToolTelemetryService toolTelemetryService) {
        this(workingDirectoryService, toolActivityService, toolTelemetryService, resolveEmbeddingService());
    }

    public IntrospectToolset(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService,
            final ToolTelemetryService toolTelemetryService,
            final EmbeddingService embeddingService) {
        this(new IntrospectToolSupport(workingDirectoryService, toolActivityService, toolTelemetryService),
                embeddingService);
    }

    IntrospectToolset(final IntrospectToolSupport support, final EmbeddingService embeddingService) {
        super(buildToolList(support, embeddingService));
    }

    private static List<ToolMacro> buildToolList(final IntrospectToolSupport support,
            final EmbeddingService embeddingService) {
        final var tools = new java.util.ArrayList<ToolMacro>();
        tools.add(new ReadFolderManifestTool(support));
        tools.add(new ReadProjectManifestTool(support));
        tools.add(new ReadProjectKnowledgeTool(support));
        tools.add(new GetSessionContextTool(support));
        tools.add(new OpenWorkspaceCanvasTool(support));

        // Add embedding tools if service is available
        if (embeddingService != null) {
            tools.add(new GetEmbeddingContextToolImpl(embeddingService));
            tools.add(new GetRecentEmbeddingsToolImpl(embeddingService));
        }

        return tools;
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

    private static EmbeddingService resolveEmbeddingService() {
        if (isCdiAvailable()) {
            final var instance = Arc.container().instance(EmbeddingService.class);
            if (instance != null && instance.isAvailable()) {
                return instance.get();
            }
        }
        return null;
    }
}
