package ac.uk.sussex.kn253.api.model;

import ac.uk.sussex.kn253.model.Project;

/** Response body for {@code GET /api/projects}. */
public record ProjectSummaryResponse(
                Long id,
                String directory,
                boolean hasGitRepository) {

        public ProjectSummaryResponse(final Project project) {
                this(project.id, project.getDirectory(), project.getGitRepository() != null);
        }
}
