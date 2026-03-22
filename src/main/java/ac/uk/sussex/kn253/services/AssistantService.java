package ac.uk.sussex.kn253.services;

import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssistantService {

    @Inject
    ChatService chatService;

    public void launch() {
        try {
            final Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            final LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            System.out.println("\nAssistant ready. Type 'back' to return to the main menu.\n");
            while (true) {
                try {
                    final String message = reader.readLine("You: ");
                    if (message == null || message.trim().equalsIgnoreCase("back")) {
                        break;
                    }
                    if (message.isBlank()) {
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
}
