package ac.uk.sussex.kn253.services;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DebugMenu {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Inject
    WorkingDirectoryService workingDirectoryService;

    @Inject
    ToolActivityService toolActivityService;

    public void run(final LineReader reader) {
        while (true) {
            printMenu();
            try {
                final String input = reader.readLine("debug> ").trim();
                switch (input) {
                    case "1" -> printCurrentWorkingDirectory();
                    case "2" -> printRecentToolTraces(20);
                    case "3" -> printRecentToolTraces(promptLimit(reader));
                    case "4" -> {
                        return;
                    }
                    default -> System.out.println("Please choose 1-4.");
                }
            } catch (final UserInterruptException e) {
                return;
            }
        }
    }

    private void printMenu() {
        System.out.println("\n=== Debug Menu ===");
        System.out.println("  1) Print current working directory");
        System.out.println("  2) Print last 20 tool traces");
        System.out.println("  3) Print custom number of tool traces");
        System.out.println("  4) Return");
    }

    private void printCurrentWorkingDirectory() {
        System.out.println("\nCurrent working directory:");
        System.out.println("  " + workingDirectoryService.getCurrentWorkingDirectory());
    }

    private int promptLimit(final LineReader reader) {
        try {
            final String raw = reader.readLine("How many traces? [20]: ").trim();
            if (raw.isEmpty()) {
                return 20;
            }
            final int value = Integer.parseInt(raw);
            return Math.max(1, Math.min(200, value));
        } catch (final NumberFormatException e) {
            System.out.println("Invalid number, using 20.");
            return 20;
        } catch (final UserInterruptException e) {
            return 20;
        }
    }

    private void printRecentToolTraces(final int limit) {
        final List<ToolActivityService.ToolActivityEvent> events = toolActivityService.recent(limit);
        if (events.isEmpty()) {
            System.out.println("\nNo tool traces recorded yet.");
            return;
        }

        System.out.println("\nRecent tool traces (latest first):");
        for (int i = events.size() - 1; i >= 0; i--) {
            final ToolActivityService.ToolActivityEvent event = events.get(i);
            final String timestamp = formatTimestamp(event.timestamp());

            System.out.println("\n---");
            System.out.println("Time: " + timestamp);
            System.out.println("Tool: " + event.toolName());
            if (event.requestedFiles() != null && !event.requestedFiles().isEmpty()) {
                System.out.println("Files: " + String.join(", ", event.requestedFiles()));
            }
            if (event.argumentsJson() != null && !event.argumentsJson().isBlank()) {
                System.out.println("Args: " + event.argumentsJson());
            }
            if (event.result() != null && !event.result().isBlank()) {
                System.out.println("Result:");
                System.out.println(event.result());
            }
        }
        System.out.println("\n---\nEnd of traces.");
    }

    private String formatTimestamp(final String rawIsoInstant) {
        try {
            return TS_FORMAT.format(java.time.Instant.parse(rawIsoInstant));
        } catch (final Exception e) {
            return rawIsoInstant;
        }
    }
}
