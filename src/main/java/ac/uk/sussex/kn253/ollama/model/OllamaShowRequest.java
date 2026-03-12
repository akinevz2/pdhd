package ac.uk.sussex.kn253.ollama.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.*;

/**
 * Request body for {@code POST /api/show}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaShowRequest {

    /** Name of the model to inspect. */
    private String name;
}
