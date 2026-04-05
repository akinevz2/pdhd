package ac.uk.sussex.kn253.ollama;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import ac.uk.sussex.kn253.repository.OllamaModelInfo;

/**
 * Represents a model that is currently loaded in Ollama's memory,
 * as returned by {@code GET /api/ps}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaRunningModel(
        String name,
        String model,
        long size,
        String digest,
        OllamaModelInfo.Details details,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("size_vram") long sizeVram) {
    public String getName() {
        return name;
    }

    public String getModel() {
        return model;
    }

    public long getSize() {
        return size;
    }

    public String getDigest() {
        return digest;
    }

    public OllamaModelInfo.Details getDetails() {
        return details;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getSizeVram() {
        return sizeVram;
    }
}
