package ac.uk.sussex.kn253.api.model;

public record ProjectSummaryResponse(
        Long id,
        String directory,
        boolean hasGitRepository,
        String githubName,
        String githubDescription) {
}
