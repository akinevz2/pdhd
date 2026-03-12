package ac.uk.sussex.kn253.ollama.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from {@code POST /api/show} – detailed information about a single
 * model.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaShowResponse {

    /** Raw Modelfile content. */
    private String modelfile;

    /** Model parameters as a string (e.g. temperature, top_p). */
    private String parameters;

    /** Model template string. */
    private String template;

    /** System prompt embedded in the model. */
    private String system;

    /** Detailed model metadata. */
    private OllamaModelInfo.Details details;

    /** Timestamp when the model was last modified. */
    @JsonProperty("modified_at")
    private Instant modifiedAt;

    /** Size of the model on disk in bytes. */
    private long size;

    /** SHA-256 digest. */
    private String digest;
}
