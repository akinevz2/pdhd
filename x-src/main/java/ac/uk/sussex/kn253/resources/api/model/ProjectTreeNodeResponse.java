package ac.uk.sussex.kn253.resources.api.model;

import java.util.List;

/**
 * A single node in the file-tree response for
 * {@code GET /api/projects/{id}/tree}.
 */
public record ProjectTreeNodeResponse(
                String name,
                String relativePath,
                boolean directory,
                List<ProjectTreeNodeResponse> children) {
}
