package ac.uk.sussex.kn253;

import java.io.IOException;
import java.util.List;

import org.jline.prompt.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import ac.uk.sussex.kn253.menu.*;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

@TopCommand()
@Command(description = MainMenu.DESCRIPTION, name = MainMenu.NAME, mixinStandardHelpOptions = true, subcommands = {
        AssistantMenu.class,
        OllamaConfigMenu.class,
        DebugMenu.class,
        WebUiMenu.class,
        ExitCommand.class
})
@ApplicationScoped
public class MainMenu implements Runnable {

    private static final String RETURN_TO_MAIN_MENU = "Returning to main menu...";

    private enum SelectorOutcome {
        SHUTDOWN,
        KEEP_RUNNING
    }

    @Inject
    IFactory factory;

    @Inject
    AssistantMenu assistantMenu;

    @Inject
    OllamaConfigMenu ollamaConfigMenu;

    @Inject
    DebugMenu debugMenu;

    @Inject
    WebUiMenu webUiMenu;

    @Inject
    ExitCommand exitCommand;

    private void initializeRuntime() throws IOException {
        ensureCommandLineInitialized();
        terminal = getTerminal();
    }

    @Override
    public void run() {
        try {
            initializeRuntime();
            runMenuLoop();
        } catch (final IOException e) {
            throw new RuntimeException("Main menu initialization failed", e);
        }
    }

    private void runMenuLoop() {
        try {
            while (true) {
                final SelectorOutcome outcome = runInteractiveSelector();
                if (outcome == SelectorOutcome.SHUTDOWN) {
                    break;
                }
                // KEEP_RUNNING means a submenu returned and we should re-display main menu.
            }
        } catch (final IOException e) {
            throw new RuntimeException("Main menu loop failed", e);
        }
    }

    static {
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        }
        if (System.getProperty("org.jboss.logging.provider") == null) {
            System.setProperty("org.jboss.logging.provider", "jboss");
        }
    }

    public static final String DESCRIPTION = "Project Definition Hierarchy Discovery";
    public static final String NAME = "pdhd";

    private Terminal terminal;
    private CommandLine commandLine;

    @Named("mainTerminal")
    @Produces
    public Terminal getTerminal() {
        if (terminal == null) {
            try {
                terminal = TerminalBuilder.builder().dumb(true).system(true).build();
            } catch (final IOException e) {
                throw new IllegalStateException("Failed to initialize terminal", e);
            }
        }
        return terminal;
    }

    private void ensureCommandLineInitialized() {
        if (commandLine != null) {
            return;
        }
        commandLine = new CommandLine(this, factory);
    }

    private SelectorOutcome runInteractiveSelector() throws IOException {
        final Prompter prompter = PrompterFactory.create(terminal);
        final String choice = promptMenuSelection(
                prompter,
                "main_menu",
                "PDHD Menu",
                List.of(
                        new MenuItem("webui", "Web UI"),
                        new MenuItem("assistant", "Assistant"),
                        new MenuItem("configure", "Configure"),
                        new MenuItem("debug", "Debug"),
                        new MenuItem("exit", "Exit")));
        if (choice == null) {
            Quarkus.asyncExit();
            return SelectorOutcome.SHUTDOWN;
        }
        return switch (choice) {
            case "assistant" -> {
                executeSubcommandOrFail("assistant");
                terminal.writer().println(RETURN_TO_MAIN_MENU);
                terminal.writer().flush();
                yield SelectorOutcome.KEEP_RUNNING;
            }
            case "configure" -> {
                executeSubcommandOrFail("configure");
                terminal.writer().println(RETURN_TO_MAIN_MENU);
                terminal.writer().flush();
                yield SelectorOutcome.KEEP_RUNNING;
            }
            case "debug" -> {
                executeSubcommandOrFail("debug");
                terminal.writer().println(RETURN_TO_MAIN_MENU);
                terminal.writer().flush();
                yield SelectorOutcome.KEEP_RUNNING;
            }
            case "webui" -> {
                // Launch browser and keep Quarkus running for HTTP endpoints.
                executeSubcommandOrFail("webui");
                terminal.writer().println(RETURN_TO_MAIN_MENU);
                terminal.writer().flush();
                yield SelectorOutcome.KEEP_RUNNING;
            }
            case "exit" -> {
                Quarkus.asyncExit();
                yield SelectorOutcome.SHUTDOWN;
            }
            default -> SelectorOutcome.KEEP_RUNNING;
        };
    }

    private void executeSubcommandOrFail(final String subcommand) {
        final int exitCode = commandLine.execute(subcommand);
        if (exitCode != 0) {
            throw new IllegalStateException("Subcommand '" + subcommand + "' failed with exit code " + exitCode);
        }
    }

    private String promptMenuSelection(final Prompter prompter, final String key, final String message,
            final List<MenuItem> items) throws IOException {
        final PromptBuilder builder = prompter.newBuilder();
        final ListBuilder listBuilder = builder.createListPrompt()
                .name(key)
                .message(message)
                .pageSize(Math.max(5, Math.min(items.size(), 10)))
                .showPageIndicator(false);
        for (final MenuItem item : items) {
            listBuilder.newItem(item.id()).text(item.label()).add();
        }
        listBuilder.addPrompt();

        final PromptResult<? extends Prompt> result = prompter.prompt(List.of(), builder.build()).get(key);
        if (!(result instanceof final ListResult listResult)) {
            return null;
        }
        return listResult.getSelectedId();
    }

    private record MenuItem(String id, String label) {
    }

    public int execute(final String... args) throws Exception {
        initializeRuntime();
        return commandLine.execute(args);
    }

}
