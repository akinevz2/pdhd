package ac.uk.sussex.kn253.api.model;

import java.util.Map;

/** Request body for updating persisted Ollama settings. */
public record OllamaSettingsRequest(
        Map<String, Object> settings,
        String baseUrl,
        String modelName,
        Integer timeoutSeconds,
        Double temperature,
        Integer numPredict,
        Integer numCtx,
        String systemPrompt,
        String toolSystemPrompt) {
}