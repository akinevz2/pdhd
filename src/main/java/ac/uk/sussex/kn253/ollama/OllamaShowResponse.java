package ac.uk.sussex.kn253.ollama;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import ac.uk.sussex.kn253.repository.OllamaModelInfo;

/**
 * Response from {@code POST /api/show} – detailed information about a single
 * model.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaShowResponse(
        String modelfile,
        String parameters,
        String template,
        String system,
        OllamaModelInfo.Details details,
        @JsonProperty("modified_at") Instant modifiedAt,
        long size,
        String digest) {
    public String getModelfile() {
        return modelfile;
    }

    public String getParameters() {
        return parameters;
    }

    public String getTemplate() {
        return template;
    }

    public String getSystem() {
        return system;
    }

    public OllamaModelInfo.Details getDetails() {
        return details;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public long getSize() {
        return size;
    }

    public String getDigest() {
        return digest;
    }
}
