package ac.uk.sussex.kn253.services.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ac.uk.sussex.kn253.services.EmbeddingService;
import ac.uk.sussex.kn253.services.ProjectDiscoveryService;
import ac.uk.sussex.kn253.services.ToolActivityService;
import ac.uk.sussex.kn253.services.ToolTelemetryService;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacro;
import ac.uk.sussex.kn253.services.tools.macro.ToolMacroRegistry;
import ac.uk.sussex.kn253.services.tools.macro.explore.*;
import ac.uk.sussex.kn253.services.tools.macro.introspect.*;
import ac.uk.sussex.kn253.services.tools.macro.read.ReadFileTool;
import ac.uk.sussex.kn253.services.tools.macro.write.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Single CDI bean that owns and instantiates every {@link ToolMacro}.
 *
 * <p>This class is the canonical place where all tools are constructed. It
 * replaces the previous four per-category toolset subclasses
 * ({@code ExploreToolset}, {@code ReadToolset}, {@code WriteToolset},
 * {@code IntrospectToolset}) and the abstract {@code ToolMacroToolset} base.
 *
 * <p>Tool operation categories are derived from {@code ToolOperationType} and
 * surfaced via {@link #operationCategoryFor(String)} for analytics/telemetry.
 */
@ApplicationScoped
public class MacroToolModule implements ToolModule {

    private final ToolMacroRegistry registry;

    /** No-arg constructor for non-CDI use (e.g. unit tests without Quarkus). */
    public MacroToolModule() {
        this(new WorkingDirectoryService());
    }

    /**
     * Constructor for tests that need a specific {@link WorkingDirectoryService}
     * but do not require CDI.
     */
    public MacroToolModule(final WorkingDirectoryService workingDirectoryService) {
        this(
                new ExploreToolSupport(workingDirectoryService, null, null),
                new IntrospectToolSupport(workingDirectoryService, null, null),
                new WriteToolSupport(),
                null);
    }

    /** CDI injection constructor — all service dependencies resolved by the container. */
    @Inject
    public MacroToolModule(
            final WorkingDirectoryService workingDirectoryService,
            final ToolActivityService toolActivityService,
            final ToolTelemetryService toolTelemetryService,
            final Instance<PathSummaryLlmService> pathSummaryLlmService,
            final Instance<ProjectDiscoveryService> projectDiscoveryService,
            final Instance<EmbeddingService> embeddingService) {
        this(
                new ExploreToolSupport(workingDirectoryService, pathSummaryLlmService, projectDiscoveryService),
                new IntrospectToolSupport(workingDirectoryService, toolActivityService, toolTelemetryService),
                new WriteToolSupport(),
                embeddingService != null && embeddingService.isResolvable() ? embeddingService.get() : null);
    }

    /** Package-private constructor used in focused tests that supply support objects directly. */
    MacroToolModule(
            final ExploreToolSupport exploreSupport,
            final IntrospectToolSupport introspectSupport,
            final WriteToolSupport writeSupport,
            final EmbeddingService embeddingService) {
        this.registry = new ToolMacroRegistry(buildTools(exploreSupport, introspectSupport, writeSupport, embeddingService));
    }

    private static List<ToolMacro> buildTools(
            final ExploreToolSupport exploreSupport,
            final IntrospectToolSupport introspectSupport,
            final WriteToolSupport writeSupport,
            final EmbeddingService embeddingService) {

        final List<ToolMacro> tools = new ArrayList<>();

        // EXPLORE
        tools.add(new GetCurrentWorkingDirectoryTool(exploreSupport));
        tools.add(new ChangeWorkingDirectoryTool(exploreSupport));
        tools.add(new ResolvePathTool(exploreSupport));
        tools.add(new SearchPathsTool(exploreSupport));
        tools.add(new GetPathInfoTool(exploreSupport));
        tools.add(new ListSubdirectoriesTool(exploreSupport));
        tools.add(new ListFilesRecursiveTool(exploreSupport));
        tools.add(new AnalyzePathDetailedTool(exploreSupport));
        tools.add(new SummarizePathTool(exploreSupport));
        tools.add(new ListGitProjectsTool(exploreSupport));
        tools.add(new ListGithubProjectsTool(exploreSupport));
        tools.add(new ListProjectEntriesTool(exploreSupport));
        tools.add(new GetGitLogTool(exploreSupport));

        // READ
        tools.add(new ReadFileTool());

        // WRITE
        tools.add(new WriteFileTool(writeSupport));
        tools.add(new CreateReportTool(writeSupport));
        tools.add(new CreateTimelineTool(writeSupport));
        tools.add(new CreatePlanTool(writeSupport));
        tools.add(new AppendProjectTodoTool(writeSupport));
        tools.add(new CacheProjectKnowledgeTool(writeSupport));

        // INTROSPECT
        tools.add(new ReadFolderManifestTool(introspectSupport));
        tools.add(new ReadProjectManifestTool(introspectSupport));
        tools.add(new ReadProjectKnowledgeTool(introspectSupport));
        tools.add(new GetSessionContextTool(introspectSupport));
        tools.add(new OpenWorkspaceCanvasTool(introspectSupport));

        // INTROSPECT — optional embedding tools
        if (embeddingService != null) {
            tools.add(new GetEmbeddingContextToolImpl(embeddingService));
            tools.add(new GetRecentEmbeddingsToolImpl(embeddingService));
        }

        return tools;
    }

    @Override
    public List<ToolSpecification> toolSpecifications() {
        return registry.toolSpecifications();
    }

    @Override
    public boolean canHandle(final String toolName) {
        return registry.canHandle(toolName);
    }

    /**
     * Returns the {@code ToolOperationType} name for {@code toolName}, e.g.
     * {@code "EXPLORE"}, {@code "READ"}, {@code "WRITE"}, {@code "INTROSPECT"}.
     * Used by {@link ac.uk.sussex.kn253.services.ToolService} for telemetry grouping.
     */
    @Override
    public String operationCategoryFor(final String toolName) {
        return registry.operationType(toolName);
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
