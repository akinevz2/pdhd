package ac.uk.sussex.kn253.ollama.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;

/**
 * Request body for {@code POST /api/pull}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaPullRequest {

    /** Name of the model to pull, e.g. {@code llama3.2} or {@code llama3.2:8b}. */
    private String name;

    /**
     * When {@code true} the server streams progress events as newline-delimited
     * JSON.
     * Set to {@code false} to receive a single response when the pull is complete.
     */
    private boolean stream = false;

    /**
     * Optional: pull from a specific registry / namespace.
     * Leave {@code null} to use the default Ollama registry.
     */
    @JsonProperty("insecure")
    private Boolean insecure;
}
