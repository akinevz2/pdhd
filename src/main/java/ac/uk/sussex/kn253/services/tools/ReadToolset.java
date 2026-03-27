package ac.uk.sussex.kn253.services.tools;

import java.util.List;

import ac.uk.sussex.kn253.services.tools.macro.ToolMacroToolset;
import ac.uk.sussex.kn253.services.tools.macro.read.ReadFileTool;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Toolset that gives the AI assistant read access to project source files.
 *
 * <p>
 * Provides one tool:
 * <ul>
 * <li>{@code read_file} – read a UTF-8 text file under a project directory,
 * with an optional line-count limit to avoid overwhelming the context
 * window.</li>
 * </ul>
 *
 * <p>
 * Argument parsing is delegated to {@link ToolArguments}, and all file
 * paths are validated to remain inside the declared project root (path
 * traversal prevention).
 */
@ApplicationScoped
public class ReadToolset extends ToolMacroToolset {

    public ReadToolset() {
        super(List.of(new ReadFileTool()));
    }
}
