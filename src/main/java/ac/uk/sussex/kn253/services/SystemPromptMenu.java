package ac.uk.sussex.kn253.services;

import java.util.ArrayList;
import java.util.List;

import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import ac.uk.sussex.kn253.model.OllamaSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SystemPromptMenu {

    @Inject
    OllamaConfigService ollamaConfigService;

    @Inject
    ChatService chatService;

    public void run(final LineReader reader) {
        final OllamaSettings settings = ollamaConfigService.load();
        if (settings.getSystemPrompt() == null || settings.getSystemPrompt().isBlank()) {
            settings.setSystemPrompt(OllamaSettings.DEFAULT_SYSTEM_PROMPT);
        }

        boolean modified = false;
        while (true) {
            printMenu(settings.getSystemPrompt());
            try {
                final String input = reader.readLine("> ").trim();
                switch (input) {
                    case "1" -> printCurrentPrompt(settings.getSystemPrompt());
                    case "2" -> {
                        settings.setSystemPrompt(OllamaSettings.DEFAULT_SYSTEM_PROMPT);
                        modified = true;
                        System.out.println("Applied recommended prompt.");
                    }
                    case "3" -> {
                        final String newPrompt = promptSingleLine(reader, settings.getSystemPrompt());
                        if (newPrompt != null) {
                            settings.setSystemPrompt(newPrompt);
                            modified = true;
                        }
                    }
                    case "4" -> {
                        final String newPrompt = promptMultiLine(reader, settings.getSystemPrompt());
                        if (newPrompt != null) {
                            settings.setSystemPrompt(newPrompt);
                            modified = true;
                        }
                    }
                    case "5" -> {
                        if (settings.getSystemPrompt() == null || settings.getSystemPrompt().isBlank()) {
                            settings.setSystemPrompt(OllamaSettings.DEFAULT_SYSTEM_PROMPT);
                        }
                        ollamaConfigService.save(settings);
                        chatService.reconfigure(settings);
                        System.out.println("System prompt saved.");
                        return;
                    }
                    case "6" -> {
                        if (modified) {
                            System.out.println("Changes discarded.");
                        }
                        return;
                    }
                    default -> System.out.println("Please choose 1-6.");
                }
            } catch (final UserInterruptException e) {
                if (modified) {
                    System.out.println("Changes discarded.");
                }
                return;
            }
        }
    }

    private void printMenu(final String currentPrompt) {
        System.out.println("\n=== Configure System Prompt ===");
        final String singleLine = currentPrompt.replaceAll("\\s+", " ").trim();
        final String preview = singleLine.length() > 120 ? singleLine.substring(0, 120) + "..." : singleLine;
        System.out.println("Current prompt preview: " + preview);
        System.out.println("  1) View full current prompt");
        System.out.println("  2) Use recommended prompt");
        System.out.println("  3) Edit prompt (single line)");
        System.out.println("  4) Edit prompt (multi-line, finish with a single '.')");
        System.out.println("  5) Save and return");
        System.out.println("  6) Discard and return");
    }

    private void printCurrentPrompt(final String currentPrompt) {
        System.out.println("\n--- Current System Prompt ---");
        System.out.println(currentPrompt);
        System.out.println("--- End Prompt ---");
    }

    private String promptSingleLine(final LineReader reader, final String current) {
        try {
            final String value = reader.readLine("New prompt (blank keeps current): ").trim();
            return value.isEmpty() ? null : value;
        } catch (final UserInterruptException e) {
            return null;
        }
    }

    private String promptMultiLine(final LineReader reader, final String current) {
        System.out.println("Enter prompt lines. Type a single '.' on its own line to finish.");
        final List<String> lines = new ArrayList<>();
        try {
            while (true) {
                final String line = reader.readLine("prompt> ");
                if (".".equals(line)) {
                    break;
                }
                lines.add(line);
            }
        } catch (final UserInterruptException e) {
            return null;
        }
        if (lines.isEmpty()) {
            return null;
        }
        return String.join("\n", lines).trim();
    }
}
