package ac.uk.sussex.kn253.ollama.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level response from {@code GET /api/tags}.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaTagsResponse {

    /** List of locally available models. */
    private List<OllamaModelInfo> models;
}
