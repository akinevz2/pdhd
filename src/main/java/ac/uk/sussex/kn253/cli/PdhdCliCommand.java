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

@TopCommand
@ApplicationScoped
@Command(name = "pdhd", description = "PDHD command entry point", mixinStandardHelpOptions = true, subcommands = {
        PdhdCliCommand.AssistantCommand.class,
        PdhdCliCommand.WebUiCommand.class,
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
