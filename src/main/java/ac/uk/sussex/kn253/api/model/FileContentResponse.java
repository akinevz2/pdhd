package ac.uk.sussex.kn253.api.model;

/** Response body for file-content endpoints. */
public record FileContentResponse(
        String filePath,
        String content) {
}
