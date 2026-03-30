package ac.uk.sussex.kn253;

import org.jboss.logging.Logger;
import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import ac.uk.sussex.kn253.cli.PdhdCliCommand;
import ac.uk.sussex.kn253.services.*;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import picocli.CommandLine;

/**
 * Quarkus application entry-point for PDHD.
 *
 * <p>
 * When launched without arguments the application presents an interactive
 * JLine main menu. When CLI arguments are provided they are dispatched to
 * {@link PdhdCliCommand} instead, enabling headless/scripted usage.
 */
@QuarkusMain
@ApplicationScoped
public class Main implements QuarkusApplication {

    private static final Logger LOG = Logger.getLogger(Main.class);

    volatile boolean running = true;
    public static final String name = "Project Discovery in High Definition";
    private volatile Terminal terminal;
    private volatile Thread menuThread;

    @Inject
    ChatService chatService;

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

    @Inject
    @TopCommand
    PdhdCliCommand pdhdCliCommand;

    @Inject
    CommandLine.IFactory commandFactory;

    public static void main(final String[] args) {
        Quarkus.run(Main.class, args);
    }

    @Override
    public int run(final String... args) throws Exception {
        if (args != null && args.length > 0) {
            return new CommandLine(pdhdCliCommand, commandFactory).execute(args);
        }

        menuThread = Thread.currentThread();

        final Terminal terminal = TerminalBuilder.builder()
                .provider("jna")
                .system(true)
                .build();
        this.terminal = terminal;

        final LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        while (running) {
            System.out.println("\n=== " + name + " ===");
            System.out.println("1. Launch assistant");
            System.out.println("2. Launch web UI");
            System.out.println("3. Debug menu");
            System.out.println("4. Configure Ollama");
            System.out.println("5. Configure system prompt");
            System.out.println("6. Exit");

            try {
                final String input = reader.readLine("> ");
                switch (input.trim()) {
                    case "1" -> assistant.launch();
                    case "2" -> webui.launch();
                    case "3" -> debugMenu.run(reader);
                    case "4" -> ollamaConfigMenu.run(reader);
                    case "5" -> systemPromptMenu.run(reader);
                    case "6" -> exit();
                    default -> System.out.println("Please choose 1, 2, 3, 4, 5, or 6.");
                }
            } catch (final UserInterruptException e) {
                exit();
            } catch (final EndOfFileException e) {
                if (running) {
                    exit();
                }
            }
        }

        Quarkus.waitForExit();
        return 0;
    }

    public void exit() {
        System.out.println("\nExiting...");
        running = false;
        final Thread currentMenuThread = menuThread;
        if (currentMenuThread != null && currentMenuThread != Thread.currentThread()) {
            currentMenuThread.interrupt();
        }
        final Terminal currentTerminal = terminal;
        if (currentTerminal != null) {
            try {
                currentTerminal.close();
            } catch (final Exception e) {
                LOG.debugf("Exception while closing terminal: %s", e.getMessage());
            }
        }
        Quarkus.asyncExit();
    }
}
