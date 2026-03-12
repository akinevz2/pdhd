package ac.uk.sussex.kn253.ollama.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single model entry returned by the Ollama {@code /api/tags}
 * endpoint.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaModelInfo {

    /** Fully-qualified model name, e.g. {@code llama3.2:latest}. */
    private String name;

    /** Short display name without the tag. */
    private String model;

    /** Size of the model on disk in bytes. */
    private long size;

    /** SHA-256 digest of the model blob. */
    private String digest;

    /** Timestamp when the model was last modified. */
    @JsonProperty("modified_at")
    private Instant modifiedAt;

    /** Nested model details (family, parameter size, quantisation, etc.). */
    private Details details;

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    /**
     * Detailed metadata about a model's architecture and quantisation.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Details {

        /** Model family, e.g. {@code llama}. */
        private String family;

        /** List of model families this model belongs to. */
        private java.util.List<String> families;

        /** Human-readable parameter count, e.g. {@code 3.2B}. */
        @JsonProperty("parameter_size")
        private String parameterSize;

        /** Quantisation level, e.g. {@code Q4_K_M}. */
        @JsonProperty("quantization_level")
        private String quantizationLevel;

        /** Format of the model file, e.g. {@code gguf}. */
        private String format;
    }
}
