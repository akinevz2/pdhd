package ac.uk.sussex.kn253.ollama;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Top-level response from {@code GET /api/ps} – lists models currently loaded
 * in memory.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaPsResponse(List<OllamaRunningModel> models) {
    public List<OllamaRunningModel> getModels() {
        return models;
    }
}
