package ac.uk.sussex.kn253.cli;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import ac.uk.sussex.kn253.services.*;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Top-level Picocli command for the {@code pdhd} CLI.
 *
 * <p>
 * Each sub-command delegates to the corresponding service or menu bean
 * injected into the parent {@link PdhdCliCommand} instance. A new JLine
 * {@link org.jline.reader.LineReader} is built on demand (via
 * {@link #newReader()}) for sub-commands that require interactive terminal
 * input.
 *
 * <p>
 * Running {@code pdhd} without a sub-command prints usage help.
 */
@TopCommand
@ApplicationScoped
@Command(name = "pdhd", description = "PDHD command entry point", mixinStandardHelpOptions = true, subcommands = {
        PdhdCliCommand.AssistantCommand.class,
        PdhdCliCommand.WebUiCommand.class,
        PdhdCliCommand.DebugCommand.class,
        PdhdCliCommand.ConfigureOllamaCommand.class,
        PdhdCliCommand.ConfigureSystemPromptCommand.class,
        PdhdCliCommand.ExitCommand.class
})
public class PdhdCliCommand implements Runnable {

    @Inject
    AssistantService assistant;

    @Inject
    WebUiService webui;

    @Inject
    OllamaConfigMenu ollamaConfigMenu;

    @Inject
    SystemPromptMenu systemPromptMenu;

    @Inject
    DebugMenu debugMenu;

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    private LineReader newReader() {
        try {
            final Terminal terminal = TerminalBuilder.builder()
                    .provider("jna")
                    .system(true)
                    .build();
            return LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to initialize terminal", e);
        }
    }

    private abstract static class BaseSubcommand implements Runnable {
        @ParentCommand
        PdhdCliCommand parent;
    }

    @Command(name = "assistant", description = "Launch assistant")
    static class AssistantCommand extends BaseSubcommand {
        @Override
        public void run() {
            parent.assistant.launch();
        }
    }

    @Command(name = "webui", description = "Launch web UI")
    static class WebUiCommand extends BaseSubcommand {
        @Override
        public void run() {
            parent.webui.launch();
        }
    }

    @Command(name = "debug", description = "Open debugging menu")
    static class DebugCommand extends BaseSubcommand {
        @Override
        public void run() {
            parent.debugMenu.run(parent.newReader());
        }
    }

    @Command(name = "configure-ollama", description = "Open Ollama configuration menu")
    static class ConfigureOllamaCommand extends BaseSubcommand {
        @Override
        public void run() {
            parent.ollamaConfigMenu.run(parent.newReader());
        }
    }

    @Command(name = "configure-system-prompt", description = "Open system prompt configuration menu")
    static class ConfigureSystemPromptCommand extends BaseSubcommand {
        @Override
        public void run() {
            parent.systemPromptMenu.run(parent.newReader());
        }
    }

    @Command(name = "exit", description = "Exit application")
    static class ExitCommand extends BaseSubcommand {
        @Override
        public void run() {
            System.out.println("Exiting...");
            Quarkus.asyncExit();
        }
    }
}
