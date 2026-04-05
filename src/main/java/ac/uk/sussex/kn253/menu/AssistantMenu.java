package ac.uk.sussex.kn253.menu;

import java.io.IOException;
import java.util.Objects;

import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import ac.uk.sussex.kn253.services.AssistantService;
import ac.uk.sussex.kn253.services.ModelConfigService;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
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

    @Override
    public void run() {
        resolveDependencies();
        final var lineReader = LineReaderBuilder.builder().terminal(terminal).build();
        final var writer = new PrintAboveWriter(lineReader);
        final var prompter = new AssistantPrompt(lineReader, writer, resolveAssistantRole());
        conversationLoop(prompter);
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
    }

    private String resolveAssistantRole() {
        final var settings = modelConfigService.load();
        if (settings == null || settings.getModelName() == null || settings.getModelName().isBlank()) {
            return ASSISTANT_ROLE_FALLBACK;
        }
        return "assistant(" + settings.getModelName().trim() + ")> ";
    }

    private void conversationLoop(final AssistantPrompt prompter) {
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
            final String response = assistantService.chat(trimmed);
            final long elapsedMillis = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            final int tokens = estimateTokens(response);
            final double tps = tokens / (elapsedMillis / 1000.0d);
            prompter.output(response,
                    new ModelStatistics(ModelStatus.WRITING, tps, tokens, elapsedMillis));
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

    public record AssistantPrompt(LineReader lineReader, PrintAboveWriter writer, String assistantRole) {

        public String prompt() {
            return lineReader.readLine(USER_ROLE);
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