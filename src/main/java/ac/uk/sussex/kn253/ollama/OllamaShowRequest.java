package ac.uk.sussex.kn253.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /api/show}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaShowRequest(String name) {
    public String getName() {
        return name;
    }
}
