package ac.uk.sussex.kn253.api.model;

/** Response body for {@code GET /api/projects}. */
public record ProjectSummaryResponse(
        Long id,
        String directory,
        boolean hasGitRepository) {
}
