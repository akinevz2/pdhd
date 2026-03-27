package ac.uk.sussex.kn253.services;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import ac.uk.sussex.kn253.model.OllamaSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Interactive JLine terminal menu for configuring the Ollama connection.
 *
 * <p>
 * Call {@link #run(LineReader)} from the main menu to enter the sub-menu.
 * Changes are only written to the database when the user chooses "Save".
 */
@ApplicationScoped
public class OllamaConfigMenu {

    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    @Inject
    OllamaConfigService ollamaConfigService;

    @Inject
    ChatService chatService;

    /** Enters the interactive configuration sub-menu. */
    public void run(final LineReader reader) {
        final OllamaSettings settings = ollamaConfigService.load();
        boolean modified = false;

        while (true) {
            printMenu(settings);
            try {
                final String input = reader.readLine("> ").trim();
                switch (input) {
                    case "1" -> {
                        final String val = prompt(reader, "Base URL", settings.getBaseUrl());
                        if (val != null) {
                            settings.setBaseUrl(val);
                            modified = true;
                        }
                    }
                    case "2" -> {
                        final String val = pickModel(reader, settings.getBaseUrl(), settings.getModelName());
                        if (val != null) {
                            settings.setModelName(val);
                            modified = true;
                        }
                    }
                    case "3" -> {
                        final Integer val = promptInt(reader,
                                "Timeout in seconds", settings.getTimeoutSeconds());
                        if (val != null) {
                            settings.setTimeoutSeconds(val);
                            modified = true;
                        }
                    }
                    case "4" -> {
                        final Double val = promptDouble(reader,
                                "Temperature (0.0 – 2.0)", settings.getTemperature());
                        if (val != null) {
                            settings.setTemperature(val);
                            modified = true;
                        }
                    }
                    case "5" -> {
                        final Integer val = promptInt(reader,
                                "Num predict (-1 = model default)", settings.getNumPredict());
                        if (val != null) {
                            settings.setNumPredict(val);
                            modified = true;
                        }
                    }
                    case "6" -> {
                        final Integer val = promptInt(reader,
                                "Context window in tokens (0 = model default)", settings.getNumCtx());
                        if (val != null) {
                            settings.setNumCtx(val);
                            modified = true;
                        }
                    }
                    case "7" -> listModels(settings.getBaseUrl());
                    case "8" -> {
                        ollamaConfigService.save(settings);
                        chatService.reconfigure(settings);
                        System.out.println("Settings saved.");
                        return;
                    }
                    case "9" -> {
                        if (modified) {
                            System.out.println("Changes discarded.");
                        }
                        return;
                    }
                    default -> System.out.println("Please choose 1–9.");
                }
            } catch (final UserInterruptException e) {
                if (modified) {
                    System.out.println("Changes discarded.");
                }
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void printMenu(final OllamaSettings s) {
        System.out.println("\n=== Configure Ollama ===");
        System.out.printf("  1) Base URL        : %s%n", s.getBaseUrl());
        System.out.printf("  2) Model           : %s%n", s.getModelName());
        System.out.printf("  3) Timeout (s)     : %d%n", s.getTimeoutSeconds());
        System.out.printf("  4) Temperature     : %.2f%n", s.getTemperature());
        System.out.printf("  5) Num predict     : %d%n", s.getNumPredict());
        System.out.printf("  6) Context window  : %d%n", s.getNumCtx());
        System.out.println("  ─────────────────────────────");
        System.out.println("  7) List models on server");
        System.out.println("  8) Save and return");
        System.out.println("  9) Discard and return");
    }

    /**
     * Prompts for a string, returning {@code null} if the input is blank (keep
     * current).
     */
    private String prompt(final LineReader reader, final String label, final String current) {
        try {
            final String val = reader.readLine(label + " [" + current + "]: ").trim();
            return val.isEmpty() ? null : val;
        } catch (final UserInterruptException e) {
            return null;
        }
    }

    private Integer promptInt(final LineReader reader, final String label, final int current) {
        try {
            final String val = reader.readLine(label + " [" + current + "]: ").trim();
            if (val.isEmpty()) {
                return null;
            }
            return Integer.parseInt(val);
        } catch (final NumberFormatException e) {
            System.out.println("Invalid integer – value unchanged.");
            return null;
        } catch (final UserInterruptException e) {
            return null;
        }
    }

    private Double promptDouble(final LineReader reader, final String label, final double current) {
        try {
            final String val = reader.readLine(
                    String.format("%s [%.2f]: ", label, current)).trim();
            if (val.isEmpty()) {
                return null;
            }
            return Double.parseDouble(val);
        } catch (final NumberFormatException e) {
            System.out.println("Invalid number – value unchanged.");
            return null;
        } catch (final UserInterruptException e) {
            return null;
        }
    }

    /**
     * Shows models available on the server (using the current baseUrl being
     * edited),
     * then lets the user pick by number or type a name manually.
     */
    private String pickModel(final LineReader reader, final String baseUrl, final String current) {
        final List<String> models = fetchModelNames(baseUrl);
        if (models.isEmpty()) {
            System.out.println("(Could not reach Ollama server – enter model name manually)");
            return prompt(reader, "Model name", current);
        }

        System.out.println("\nAvailable models on " + baseUrl + ":");
        for (int i = 0; i < models.size(); i++) {
            System.out.printf("  %d) %s%n", i + 1, models.get(i));
        }
        try {
            final String input = reader.readLine(
                    "Pick number or type model name [" + current + "]: ").trim();
            if (input.isEmpty()) {
                return null;
            }
            try {
                final int idx = Integer.parseInt(input) - 1;
                if (idx >= 0 && idx < models.size()) {
                    return models.get(idx);
                }
            } catch (final NumberFormatException ignored) {
                // treat as a literal model name
            }
            return input;
        } catch (final UserInterruptException e) {
            return null;
        }
    }

    private void listModels(final String baseUrl) {
        System.out.println("\nQuerying " + baseUrl + " …");
        final List<String> models = fetchModelNames(baseUrl);
        if (models.isEmpty()) {
            System.out.println("No models found (server may be unreachable).");
        } else {
            System.out.println("Models:");
            models.forEach(m -> System.out.println("  • " + m));
        }
    }

    /**
     * Queries {@code <baseUrl>/api/tags} and extracts model names.
     * Returns an empty list on any error.
     */
    private List<String> fetchModelNames(final String baseUrl) {
        try {
            final String url = baseUrl.replaceAll("/+$", "") + "/api/tags";
            final HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }
            final Matcher matcher = MODEL_NAME_PATTERN.matcher(response.body());
            final List<String> names = new ArrayList<>();
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            return names;
        } catch (final Exception e) {
            return List.of();
        }
    }
}
