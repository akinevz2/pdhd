package ac.uk.sussex.kn253.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single progress event returned by {@code POST /api/pull}.
 *
 * <p>
 * When streaming is disabled only the final status object is returned.
 * When streaming is enabled, multiple objects are returned (one per line)
 * until the pull is complete.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaPullStatus(String status, String digest, long total, long completed) {

    /** {@code true} if the pull finished successfully. */
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }

    public String getStatus() {
        return status;
    }

    public String getDigest() {
        return digest;
    }

    public long getTotal() {
        return total;
    }

    public long getCompleted() {
        return completed;
    }
}
