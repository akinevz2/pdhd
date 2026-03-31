package ac.uk.sussex.kn253.services.tools.macro.introspect;

import java.nio.file.Path;
import java.util.Map;

// FIXME: TERRIBLE IMPLEMENTATION
public class DefaultFolderManifestShaper implements FolderManifestShaper {

    private static final String UNKNOWN_CONTENT_HINT = "Only files listed in this section were read for content. For all other files, content is unknown unless read via read_file.";
    private static final String UNKNOWN_SRC_CONTENT_HINT = "Only files listed in this section were read for content. For any file not listed here, content is unknown unless read via read_file.";

    @Override
    public String shapeFolderManifest(final FolderManifestShapeInput input) {
        final Path directory = input.directory();
        final StringBuilder sb = new StringBuilder();
        sb.append("Folder directory: ").append(directory).append("\n\n");

        if (input.entries().isEmpty()) {
            sb.append("The folder is empty.");
            return sb.toString();
        }

        sb.append("=== folder entries (recursive) ===\n");
        for (final Path entry : input.entries()) {
            final String rel = normalize(directory.relativize(entry));
            sb.append("- ").append(rel);
            if (java.nio.file.Files.isDirectory(entry)) {
                sb.append("/");
            }
            sb.append("\n");
        }

        if (!input.sampledFileContents().isEmpty()) {
            sb.append("\n=== sampled file contents (evidence only) ===\n");
            sb.append(UNKNOWN_CONTENT_HINT).append("\n\n");

            for (final Map.Entry<Path, String> sampled : input.sampledFileContents().entrySet()) {
                final String rel = normalize(directory.relativize(sampled.getKey()));
                sb.append("--- ").append(rel).append(" ---\n");
                sb.append(sampled.getValue()).append("\n\n");
            }

            if (input.files().size() > input.sampledFileContents().size()) {
                sb.append("Content omitted for ")
                        .append(input.files().size() - input.sampledFileContents().size())
                        .append(" file(s) in this folder tree. Use read_file for exact content when needed.\n");
            }
        }

        return sb.toString().trim();
    }

    @Override
    public String shapeProjectManifest(final ProjectManifestShapeInput input) {
        final Path directory = input.directory();
        final StringBuilder sb = new StringBuilder();
        sb.append("Project directory: ").append(directory).append("\n\n");

        if (input.manifestContents().isEmpty()) {
            sb.append("No standard project manifest files found in this directory.\n");
            sb.append("Consider using list_project_entries to see what files are present.");
        } else {
            for (final Map.Entry<String, String> entry : input.manifestContents().entrySet()) {
                sb.append("=== ").append(entry.getKey()).append(" ===\n");
                sb.append(entry.getValue()).append("\n\n");
            }
        }

        if (input.sourceScanError() != null) {
            sb.append("\n=== src/ (recursive) ===\n");
            sb.append("Failed to list src recursively: ").append(input.sourceScanError()).append("\n");
            return sb.toString().trim();
        }

        if (input.sourceRoot() == null || input.sourceFiles().isEmpty()) {
            return sb.toString().trim();
        }

        sb.append("\n=== src/ (recursive) ===\n");
        for (final Path sourceFile : input.sourceFiles()) {
            sb.append("- ").append(normalize(input.sourceRoot().relativize(sourceFile))).append("\n");
        }

        if (!input.sampledSourceContents().isEmpty()) {
            sb.append("\n=== src/ sampled file contents (evidence only) ===\n");
            sb.append(UNKNOWN_SRC_CONTENT_HINT).append("\n\n");

            for (final Map.Entry<Path, String> sampled : input.sampledSourceContents().entrySet()) {
                sb.append("--- ")
                        .append(normalize(input.sourceRoot().relativize(sampled.getKey())))
                        .append(" ---\n");
                sb.append(sampled.getValue()).append("\n\n");
            }

            if (input.sourceFiles().size() > input.sampledSourceContents().size()) {
                sb.append("Content omitted for ")
                        .append(input.sourceFiles().size() - input.sampledSourceContents().size())
                        .append(" src file(s). Use read_file for exact content when needed.\n");
            }
        }

        return sb.toString().trim();
    }

    private String normalize(final Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }
}
