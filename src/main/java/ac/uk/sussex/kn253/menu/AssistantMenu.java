package ac.uk.sussex.kn253.menu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import ac.uk.sussex.kn253.services.*;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import picocli.CommandLine.Command;

@ApplicationScoped
@Command(name = "assistant", description = "Launch assistant")
public class AssistantMenu implements Runnable {

    private static final String ASSISTANT_ROLE_FALLBACK = AssistantService.DEFAULT_PROMPT_PREFIX;
    private static final String USER_ROLE = "user> ";
    private static final String EXIT_COMMAND = "exit";

    private static final CharSequence THINKING = "(waking up...)";

    private enum ModelStatus {
        THINKING,
        WRITING
    }

    private record ModelStatistics(ModelStatus status, double tokensPerSecond, int tokenCount, long elapsedMillis) {
    }

    @Named("mainTerminal")
    @Inject
    Terminal terminal;

    @Inject
    AssistantService assistantService;

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    AssistantWorkingDirectoryService assistantWorkingDirectoryService;

    @Inject
    RequestContextController requestContextController;

    @Inject
    TelemetryService telemetryService;

    @Override
    public void run() {
        final boolean activated = requestContextController.activate();
        try {
            resolveDependencies();
            final var lineReader = LineReaderBuilder.builder().terminal(terminal).build();
            final var writer = new PrintAboveWriter(lineReader);
            final String modelName = resolveModelName();
            final var prompter = new AssistantPrompt(
                    lineReader,
                    writer,
                    resolveAssistantRole(modelName),
                    () -> formatUserPrompt(modelName, assistantWorkingDirectoryService.getCurrentWorkingDirectory()));
            conversationLoop(prompter, modelName);
        } finally {
            if (activated) {
                requestContextController.deactivate();
            }
        }
    }

    private void resolveDependencies() {
        if (terminal == null) {
            try {
                terminal = TerminalBuilder.builder().dumb(true).system(true).build();
            } catch (final IOException e) {
                throw new IllegalStateException("Failed to initialize terminal", e);
            }
        }
        if (assistantService == null) {
            assistantService = Arc.container().instance(AssistantService.class).orElse(null);
            if (assistantService == null) {
                throw new IllegalStateException("AssistantService bean unavailable");
            }
        }
        if (modelConfigService == null) {
            modelConfigService = Arc.container().instance(ModelConfigService.class).orElse(null);
            if (modelConfigService == null) {
                throw new IllegalStateException("ModelConfigService bean unavailable");
            }
        }
        if (assistantWorkingDirectoryService == null) {
            assistantWorkingDirectoryService = Arc.container().instance(AssistantWorkingDirectoryService.class)
                    .orElse(null);
            if (assistantWorkingDirectoryService == null) {
                throw new IllegalStateException("AssistantWorkingDirectoryService bean unavailable");
            }
        }
        if (telemetryService == null) {
            telemetryService = Arc.container().instance(TelemetryService.class).orElse(null);
            if (telemetryService == null) {
                throw new IllegalStateException("TelemetryService bean unavailable");
            }
        }
    }

    private String resolveModelName() {
        final var settings = modelConfigService.load();
        if (settings == null || settings.getModelName() == null || settings.getModelName().isBlank()) {
            return "assistant";
        }
        return settings.getModelName().trim();
    }

    private String resolveAssistantRole(final String modelName) {
        if (modelName == null || modelName.isBlank() || "assistant".equals(modelName)) {
            return ASSISTANT_ROLE_FALLBACK;
        }
        return "assistant(" + modelName + ")> ";
    }

    private void conversationLoop(final AssistantPrompt prompter, final String modelName) {
        prompter.output("Assistant ready. Type 'exit' to return.",
                new ModelStatistics(ModelStatus.WRITING, 0.0d, 0, 0));
        while (true) {
            final String input;
            try {
                input = prompter.prompt();
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

            prompter.output(null, new ModelStatistics(ModelStatus.THINKING, 0.0d, 0, 0));
            final long started = System.nanoTime();
            String response = null;
            RuntimeException failure = null;
            try {
                response = assistantService.chat(trimmed);
                final long elapsedMillis = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
                final int tokens = estimateTokens(response);
                final double tps = tokens / (elapsedMillis / 1000.0d);
                prompter.output(response,
                        new ModelStatistics(ModelStatus.WRITING, tps, tokens, elapsedMillis));
            } catch (final RuntimeException e) {
                failure = e;
                throw e;
            } finally {
                final long durationNanos = Math.max(0L, System.nanoTime() - started);
                telemetryService.recordModelCall(
                        modelName,
                        assistantWorkingDirectoryService.getCurrentWorkingDirectory(),
                        trimmed,
                        response,
                        estimateTokens(trimmed),
                        estimateTokens(response),
                        durationNanos,
                        failure == null ? null : failure.getClass().getName());
            }
        }
        prompter.clearStatusLine();
        prompter.output("Returning to menu...", null);
    }

    private int estimateTokens(final String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    static String formatUserPrompt(final String modelName, final String currentWorkingDirectory) {
        final String normalizedModelName = (modelName == null || modelName.isBlank()) ? "assistant" : modelName;
        return normalizedModelName + ":" + shortenPath(currentWorkingDirectory) + " " + USER_ROLE;
    }

    static String shortenPath(final String path) {
        if (path == null || path.isBlank()) {
            return "?";
        }

        try {
            final Path normalizedPath = Path.of(path).normalize();
            final int nameCount = normalizedPath.getNameCount();
            if (nameCount == 0) {
                final Path rootOnly = normalizedPath.getRoot();
                return rootOnly == null ? "/" : rootOnly.toString();
            }

            final StringBuilder result = new StringBuilder();
            final Path root = normalizedPath.getRoot();
            if (root != null) {
                result.append(root.toString());
            }

            for (int i = 0; i < nameCount; i++) {
                if (result.length() > 0 && result.charAt(result.length() - 1) != '/') {
                    result.append('/');
                }
                final String segment = normalizedPath.getName(i).toString();
                final String value = i == nameCount - 1 ? segment : segment.substring(0, 1);
                result.append(value);
            }

            return result.toString();
        } catch (final RuntimeException ex) {
            return path;
        }
    }

    public record AssistantPrompt(LineReader lineReader, PrintAboveWriter writer, String assistantRole,
            Supplier<String> userPromptSupplier) {

        public String prompt() {
            return lineReader.readLine(userPromptSupplier.get());
        }

        public void output(final String text, final ModelStatistics stats) {
            final var asb = new AttributedStringBuilder();
            final var style = AttributedStyle.DEFAULT;
            final var color = AttributedStyle.GREEN;
            final String statusLine = formatStatusLine(stats);
            if (Objects.nonNull(statusLine)) {
                writer.write(statusLine);
                writer.flush();
            }
            asb.style(AttributedStyle.BOLD.foreground(color)).append(assistantRole).style(style)
                    .append(text == null ? THINKING : text);
            writer.write(asb.toAnsi());
            writer.flush();
        }

        public void clearStatusLine() {
            // Clear any transient status line before handing control back to the main menu.
            writer.write("\r\u001B[2K");
            writer.flush();
        }

        private String formatStatusLine(final ModelStatistics stats) {
            if (stats == null)
                return null;

            return String.format("[%s] tps=%.2f tokens=%d elapsed=%dms", stats.status(),
                    Math.max(0.0d, stats.tokensPerSecond()),
                    Math.max(0, stats.tokenCount()),
                    Math.max(0L, stats.elapsedMillis()));
        }
    }

}