package ac.uk.sussex.kn253.resources.api.model;

/** Response body for file-content endpoints. */
public record FileContentResponse(
        String filePath,
        String content) {
}
