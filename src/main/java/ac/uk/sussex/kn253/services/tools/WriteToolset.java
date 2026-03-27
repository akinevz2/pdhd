package ac.uk.sussex.kn253.services.tools;

import java.util.List;

import ac.uk.sussex.kn253.services.tools.macro.ToolMacroToolset;
import ac.uk.sussex.kn253.services.tools.macro.write.*;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Toolset that gives the AI assistant the ability to write files and create
 * structured project artefacts.
 *
 *
 * <p>
 * All output paths are validated to remain inside the declared project root
 * (path traversal prevention). Argument parsing is delegated to
 * {@link ToolArguments}.
 */
@ApplicationScoped
public class WriteToolset extends ToolMacroToolset {

    public WriteToolset() {
        this(new WriteToolSupport());
    }

    WriteToolset(final WriteToolSupport support) {
        super(List.of(
                new WriteFileTool(support),
                new CreateReportTool(support),
                new CreateTimelineTool(support),
                new CreatePlanTool(support),
                new AppendProjectTodoTool(support),
                new CacheProjectKnowledgeTool(support)));
    }
}
