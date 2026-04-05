package ac.uk.sussex.kn253.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code POST /api/pull}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaPullRequest(String name, boolean stream, @JsonProperty("insecure") Boolean insecure) {
    public String getName() {
        return name;
    }

    public boolean isStream() {
        return stream;
    }

    public Boolean getInsecure() {
        return insecure;
    }
}
