package ac.uk.sussex.kn253.ollama.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a model that is currently loaded in Ollama's memory,
 * as returned by {@code GET /api/ps}.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaRunningModel {

    /** Fully-qualified model name. */
    private String name;

    /** Short model identifier. */
    private String model;

    /** Size of the model in bytes. */
    private long size;

    /** SHA-256 digest. */
    private String digest;

    /** Detailed model metadata. */
    private OllamaModelInfo.Details details;

    /** When this model will be evicted from memory if idle. */
    @JsonProperty("expires_at")
    private Instant expiresAt;

    /** VRAM used by this model in bytes. */
    @JsonProperty("size_vram")
    private long sizeVram;
}
