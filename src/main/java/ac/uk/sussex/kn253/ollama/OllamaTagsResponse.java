package ac.uk.sussex.kn253.ollama;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import ac.uk.sussex.kn253.repository.OllamaModelInfo;

/**
 * Top-level response from {@code GET /api/tags}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaTagsResponse(List<OllamaModelInfo> models) {
    public List<OllamaModelInfo> getModels() {
        return models;
    }
}
