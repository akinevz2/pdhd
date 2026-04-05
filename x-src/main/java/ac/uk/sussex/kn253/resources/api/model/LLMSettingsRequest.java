package ac.uk.sussex.kn253.resources.api.model;

import java.util.Map;

/** Request body for updating persisted Ollama settings. */
public record LLMSettingsRequest(
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