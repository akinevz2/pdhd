package ac.uk.sussex.kn253.api.model;

import java.util.List;

public record ProjectTreeNodeResponse(
        String name,
        String relativePath,
        boolean directory,
        List<ProjectTreeNodeResponse> children) {
}
