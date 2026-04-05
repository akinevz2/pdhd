package ac.uk.sussex.kn253.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code DELETE /api/delete}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaDeleteRequest(String name) {
    public String getName() {
        return name;
    }
}
