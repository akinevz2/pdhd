package ac.uk.sussex.kn253.api.model;

/** Response body for reading persisted Ollama settings. */
public record OllamaSettingsResponse(
                String baseUrl,
                String modelName,
                int timeoutSeconds,
                double temperature,
                int numPredict,
                int numCtx,
                String systemPrompt,
                String defaultSystemPrompt,
                String toolSystemPrompt,
                String defaultToolSystemPrompt) {
}