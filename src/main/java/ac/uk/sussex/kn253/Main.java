package ac.uk.sussex.kn253;

import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Main implements QuarkusApplication {

    boolean running = true;
    public static final String name = "Project Discovery in High Definition";

    public static void main(final String[] args) {
        Quarkus.run(Main.class, args);
    }

    @Override
    public int run(final String... args) throws Exception {
        // Create terminal and line reader
        final Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        final LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        // Main menu loop
        while (running) {
            // Display menu
            System.out.println("\n=== Main Menu ===");
            System.out.println("1. Scan directories");
            System.out.println("2. Perform ls");
            System.out.println("3. Exit");
            System.out.print("Choose an option (1-3): ");

            try {
                // Read user input
                final String input = reader.readLine();

                // Process user choice
                switch (input.trim()) {
                    case "1" -> System.out.println("scanning directories");
                    case "2" -> System.out.println("printing directories");
                    case "3" -> {
                        exit();
                    }
                    default -> System.out.println("Invalid option. Please choose 1, 2, or 3.");
                }
            } catch (final UserInterruptException e) {
                // Handle Ctrl+C interruption
                exit();
            }
        }

        Quarkus.waitForExit();
        return 0;
    }

    void exit() {
        System.out.println("\nExiting...");
        running = false;
        Quarkus.asyncExit();
    }
}
