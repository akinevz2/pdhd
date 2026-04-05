package ac.uk.sussex.kn253.resources.api.model;

import java.util.List;
import java.util.Map;

/** Response body for reading persisted Ollama settings. */
public record LLMSettingsResponse(
        Map<String, Object> settings,
        List<OllamaSettingFieldResponse> settingFields,
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