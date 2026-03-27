package ac.uk.sussex.kn253.services;

import java.util.ArrayList;
import java.util.List;

import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import ac.uk.sussex.kn253.model.OllamaSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Interactive JLine terminal menu for editing the AI system prompt.
 *
 * <p>
 * Call {@link #run(LineReader)} from the main loop to enter the sub-menu.
 * The user can view the current prompt, apply the recommended default, or
 * edit it in single-line or multi-line mode. Changes are only persisted when
 * the user chooses the "Save" option.
 */
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
        if (settings.getToolSystemPrompt() == null || settings.getToolSystemPrompt().isBlank()) {
            settings.setToolSystemPrompt(OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
        }

        boolean modified = false;
        while (true) {
            printMenu(settings.getSystemPrompt(), settings.getToolSystemPrompt());
            try {
                final String input = reader.readLine("> ").trim();
                switch (input) {
                    case "1" -> printCurrentPrompts(settings.getSystemPrompt(), settings.getToolSystemPrompt());
                    case "2" -> {
                        settings.setSystemPrompt(OllamaSettings.DEFAULT_SYSTEM_PROMPT);
                        settings.setToolSystemPrompt(OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
                        modified = true;
                        System.out.println("Applied recommended prompts.");
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
                        final String newPrompt = promptSingleLine(reader, settings.getToolSystemPrompt());
                        if (newPrompt != null) {
                            settings.setToolSystemPrompt(newPrompt);
                            modified = true;
                        }
                    }
                    case "6" -> {
                        final String newPrompt = promptMultiLine(reader, settings.getToolSystemPrompt());
                        if (newPrompt != null) {
                            settings.setToolSystemPrompt(newPrompt);
                            modified = true;
                        }
                    }
                    case "7" -> {
                        if (settings.getSystemPrompt() == null || settings.getSystemPrompt().isBlank()) {
                            settings.setSystemPrompt(OllamaSettings.DEFAULT_SYSTEM_PROMPT);
                        }
                        if (settings.getToolSystemPrompt() == null || settings.getToolSystemPrompt().isBlank()) {
                            settings.setToolSystemPrompt(OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
                        }
                        ollamaConfigService.save(settings);
                        chatService.reconfigure(settings);
                        System.out.println("System prompts saved.");
                        return;
                    }
                    case "8" -> {
                        if (modified) {
                            System.out.println("Changes discarded.");
                        }
                        return;
                    }
                    default -> System.out.println("Please choose 1-8.");
                }
            } catch (final UserInterruptException e) {
                if (modified) {
                    System.out.println("Changes discarded.");
                }
                return;
            }
        }
    }

    private void printMenu(final String currentPrompt, final String currentToolPrompt) {
        System.out.println("\n=== Configure System Prompt ===");
        System.out.println("Main prompt preview: " + preview(currentPrompt));
        System.out.println("Tool prompt preview: " + preview(currentToolPrompt));
        System.out.println("  1) View full current prompts");
        System.out.println("  2) Use recommended prompts");
        System.out.println("  3) Edit main prompt (single line)");
        System.out.println("  4) Edit main prompt (multi-line, finish with a single '.')");
        System.out.println("  5) Edit tool prompt (single line)");
        System.out.println("  6) Edit tool prompt (multi-line, finish with a single '.')");
        System.out.println("  7) Save and return");
        System.out.println("  8) Discard and return");
    }

    private void printCurrentPrompts(final String currentPrompt, final String currentToolPrompt) {
        System.out.println("\n--- Main Assistant Prompt ---");
        System.out.println(currentPrompt);
        System.out.println("--- Tool Agent Prompt ---");
        System.out.println(currentToolPrompt);
        System.out.println("--- End Prompts ---");
    }

    private String preview(final String prompt) {
        final String singleLine = prompt.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 120 ? singleLine.substring(0, 120) + "..." : singleLine;
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
