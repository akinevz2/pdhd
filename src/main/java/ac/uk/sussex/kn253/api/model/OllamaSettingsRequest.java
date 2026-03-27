package ac.uk.sussex.kn253.api.model;

/** Request body for updating persisted Ollama settings. */
public record OllamaSettingsRequest(
                String baseUrl,
                String modelName,
                Integer timeoutSeconds,
                Double temperature,
                Integer numPredict,
                Integer numCtx,
                String systemPrompt,
                String toolSystemPrompt) {
}