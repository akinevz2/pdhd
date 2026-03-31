package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface FolderManifestShaper {

    String shapeFolderManifest(FolderManifestShapeInput input);

    String shapeProjectManifest(ProjectManifestShapeInput input);
}

record ManifestShapePolicy(
        int maxFolderPaths,
        int maxFolderFilesWithContent,
        int maxSourcePaths,
        int maxSourceFilesWithContent) {
}

record FolderManifestShapeInput(
        Path directory,
        List<Path> entries,
        List<Path> files,
        Map<Path, String> sampledFileContents,
        ManifestShapePolicy policy) {
}

record ProjectManifestShapeInput(
        Path directory,
        Map<String, String> manifestContents,
        Path sourceRoot,
        List<Path> sourceFiles,
        Map<Path, String> sampledSourceContents,
        String sourceScanError,
        ManifestShapePolicy policy) {
}
