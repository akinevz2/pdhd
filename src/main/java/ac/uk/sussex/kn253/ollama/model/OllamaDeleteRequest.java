package ac.uk.sussex.kn253.ollama.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.*;

/**
 * Request body for {@code DELETE /api/delete}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaDeleteRequest {

    /** Fully-qualified model name to delete, e.g. {@code llama3.2:latest}. */
    private String name;
}
