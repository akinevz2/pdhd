package ac.uk.sussex.kn253.menu;

import java.util.function.ToIntFunction;

import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;

import ac.uk.sussex.kn253.services.*;

final class AssistantSessionController {

    private static final String EXIT_COMMAND = "exit";
    private static final String USER_ROLE = "you> ";
    private static final String STATUS_THINKING = "THINKING";
    private static final String STATUS_WRITING = "WRITING";

    private final AssistantInputAdapter inputAdapter;
    private final AssistantTerminalAdapter terminalAdapter;
    private final AssistantService assistantService;
    private final TelemetryService telemetryService;
    private final AssistantWorkingDirectoryService workingDirectoryService;
    private final String modelName;
    private final String assistantRole;
    private final ToIntFunction<String> tokenEstimator;

    AssistantSessionController(
            final AssistantInputAdapter inputAdapter,
            final AssistantTerminalAdapter terminalAdapter,
            final AssistantService assistantService,
            final TelemetryService telemetryService,
            final AssistantWorkingDirectoryService workingDirectoryService,
            final String modelName,
            final String assistantRole,
            final ToIntFunction<String> tokenEstimator) {
        this.inputAdapter = inputAdapter;
        this.terminalAdapter = terminalAdapter;
        this.assistantService = assistantService;
        this.telemetryService = telemetryService;
        this.workingDirectoryService = workingDirectoryService;
        this.modelName = modelName;
        this.assistantRole = assistantRole;
        this.tokenEstimator = tokenEstimator;
    }

    void run() {
        terminalAdapter.showAssistantMessage(
                assistantRole,
                "Assistant ready. Type 'exit' to return.",
                formatStatusLine(STATUS_WRITING, 0.0d, 0, 0));
        while (true) {
            final String input;
            try {
                input = inputAdapter.readInput();
            } catch (final UserInterruptException | EndOfFileException e) {
                break;
            }

            if (input == null || input.isBlank()) {
                continue;
            }

            final String trimmed = input.trim();
            if (EXIT_COMMAND.equalsIgnoreCase(trimmed)) {
                break;
            }

            terminalAdapter.showUserMessage(USER_ROLE, trimmed);
            terminalAdapter.showStatus(formatStatusLine(STATUS_THINKING, 0.0d, 0, 0));

            final long started = System.nanoTime();
            String response = null;
            RuntimeException failure = null;
            try {
                response = assistantService.chat(trimmed);
                final long elapsedMillis = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
                final int tokens = tokenEstimator.applyAsInt(response);
                final double tps = tokens / (elapsedMillis / 1000.0d);
                terminalAdapter.showAssistantMessage(
                        assistantRole,
                        response,
                        formatStatusLine(STATUS_WRITING, tps, tokens, elapsedMillis));
            } catch (final RuntimeException e) {
                failure = e;
                throw e;
            } finally {
                final long durationNanos = Math.max(0L, System.nanoTime() - started);
                telemetryService.recordModelCall(
                        modelName,
                        workingDirectoryService.getCurrentWorkingDirectory(),
                        trimmed,
                        response,
                        tokenEstimator.applyAsInt(trimmed),
                        tokenEstimator.applyAsInt(response),
                        durationNanos,
                        failure == null ? null : failure.getClass().getName());
            }
        }

        terminalAdapter.clearTransientStatusLine();
        terminalAdapter.showAssistantMessage(assistantRole, "Returning to menu...", null);
    }

    private String formatStatusLine(final String status, final double tokensPerSecond, final int tokenCount,
            final long elapsedMillis) {
        return String.format(
                "[%s] tps=%.2f tokens=%d elapsed=%dms",
                status,
                Math.max(0.0d, tokensPerSecond),
                Math.max(0, tokenCount),
                Math.max(0L, elapsedMillis));
    }
}
