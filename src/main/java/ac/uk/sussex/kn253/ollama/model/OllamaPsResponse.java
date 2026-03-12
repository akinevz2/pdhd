package ac.uk.sussex.kn253.ollama.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level response from {@code GET /api/ps} – lists models currently loaded
 * in memory.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaPsResponse {

    /** Models currently loaded and consuming GPU/CPU memory. */
    private List<OllamaRunningModel> models;
}
