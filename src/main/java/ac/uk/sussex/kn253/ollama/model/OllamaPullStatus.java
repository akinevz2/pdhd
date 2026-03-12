package ac.uk.sussex.kn253.ollama.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single progress event returned by {@code POST /api/pull}.
 *
 * <p>
 * When streaming is disabled only the final status object is returned.
 * When streaming is enabled, multiple objects are returned (one per line)
 * until the pull is complete.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaPullStatus {

    /**
     * Human-readable status string, e.g.:
     * <ul>
     * <li>{@code pulling manifest}</li>
     * <li>{@code downloading …}</li>
     * <li>{@code verifying sha256 digest}</li>
     * <li>{@code success}</li>
     * </ul>
     */
    private String status;

    /** SHA-256 digest of the layer currently being downloaded. */
    private String digest;

    /** Total bytes to download for the current layer. */
    private long total;

    /** Bytes downloaded so far for the current layer. */
    private long completed;

    /** {@code true} if the pull finished successfully. */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }
}
