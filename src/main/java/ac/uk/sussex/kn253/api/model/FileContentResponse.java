package ac.uk.sussex.kn253.api.model;

public record FileContentResponse(
        String projectDirectory,
        String filePath,
        String content) {
}
