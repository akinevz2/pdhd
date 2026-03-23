package ac.uk.sussex.kn253.services;

import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssistantService {

    @Inject
    ChatService chatService;

    @Inject
    ToolService toolService;

    @Inject
    WorkingDirectoryService workingDirectoryService;

    public void launch() {
        try {
            final Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            final LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            System.out.println("\nAssistant ready. Type 'back' to return to the main menu.");
            System.out.println("Type '/operator' for hardcoded operator tools.\n");
            while (true) {
                try {
                    final String message = reader.readLine("You: ");
                    if (message == null || message.trim().equalsIgnoreCase("back")) {
                        break;
                    }
                    if (message.isBlank()) {
                        continue;
                    }
                    if (message.trim().equalsIgnoreCase("/operator")) {
                        runOperatorMenu(reader);
                        continue;
                    }
                    final String response = chatService.sendMessage(message.trim());
                    System.out.println("Assistant: " + response);
                } catch (final UserInterruptException e) {
                    break;
                }
            }
        } catch (final Exception e) {
            System.out.println("Failed to launch assistant: " + e.getMessage());
        }
    }

    private void runOperatorMenu(final LineReader reader) {
        while (true) {
            System.out.println("\nOperator tools:");
            System.out.println("1. Change folder");
            System.out.println("2. List directory contents");
            System.out.println("3. Back to assistant chat");

            final String choice;
            try {
                choice = reader.readLine("operator> ").trim();
            } catch (final UserInterruptException e) {
                return;
            }

            switch (choice) {
                case "1" -> runChangeFolderTool(reader);
                case "2" -> runListDirectoryContentsTool(reader);
                case "3" -> {
                    return;
                }
                default -> System.out.println("Please choose 1, 2, or 3.");
            }
        }
    }

    private void runChangeFolderTool(final LineReader reader) {
        final String pathInput;
        try {
            pathInput = reader.readLine("Path to folder: ").trim();
        } catch (final UserInterruptException e) {
            return;
        }
        if (pathInput.isBlank()) {
            System.out.println("Path is required.");
            return;
        }

        final String args = "{\"path\":\"" + escapeJson(pathInput) + "\"}";
        final String result = toolService.execute(
                ToolExecutionRequest.builder()
                        .name("change_working_directory")
                        .arguments(args)
                        .build(),
                null);
        System.out.println(result);
    }

    private void runListDirectoryContentsTool(final LineReader reader) {
        final String pathInput;
        try {
            pathInput = reader.readLine("Directory path (blank = current): ").trim();
        } catch (final UserInterruptException e) {
            return;
        }

        final String projectDirectory = pathInput.isBlank()
                ? workingDirectoryService.getCurrentWorkingDirectory().toString()
                : pathInput;

        final String args = "{\"projectDirectory\":\"" + escapeJson(projectDirectory) + "\"}";
        final String result = toolService.execute(
                ToolExecutionRequest.builder()
                        .name("list_project_entries")
                        .arguments(args)
                        .build(),
                null);
        System.out.println(result);
    }

    private String escapeJson(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
