package ac.uk.sussex.kn253.tools;

import ac.uk.sussex.kn253.services.CwdService;
import ac.uk.sussex.kn253.services.TelemetryService;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Tool set for the assistant to access filesystem operations related to the
 * current working directory.
 */
@ApplicationScoped
public class CwdTools {

    @Inject
    CwdService cwdService;

    @Inject
    TelemetryService telemetryService;

    @Tool(value = "Get the current working directory")
    public String getCurrentWorkingDirectory() {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;

        try {
            result = cwdService.getCurrentWorkingDirectory();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error getting current working directory: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                final long durationNanos = Math.max(0L, System.nanoTime() - started);
                telemetryService.recordToolUse(
                        "getCurrentWorkingDirectory",
                        "CWD",
                        "",
                        result,
                        durationNanos,
                        errorClass,
                        false);
            }
        }
    }
}
