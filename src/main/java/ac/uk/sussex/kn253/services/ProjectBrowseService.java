package ac.uk.sussex.kn253.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Handles project folder browsing: listing entries, resolving UUIDs, and
 * building file system entry records for the frontend.
 */
@ApplicationScoped
public class ProjectBrowseService {

    @Inject
    PathResolutionService pathResolutionService;

    public record FsEntry(
            String name,
            String path,
            boolean directory,
            String uuid,
            String repoUrl) {
    }

    public List<FsEntry> listEntries(final Path directory, final String repoUrl) {
        final List<Path> paths = pathResolutionService.listDirectoryEntries(directory);
        return paths.stream()
                .map(path -> new FsEntry(
                        path.getFileName().toString(),
                        path.toAbsolutePath().normalize().toString(),
                        Files.isDirectory(path),
                        pathResolutionService.uuidForPath(path),
                        ".git".equals(path.getFileName().toString()) ? repoUrl : null))
                .toList();
    }

    public FsEntry toEntry(final Path path, final String projectRepoUrl) {
        final String name = path.getFileName().toString();
        final boolean directory = Files.isDirectory(path);
        final String absolutePath = path.toAbsolutePath().normalize().toString();
        final String uuid = pathResolutionService.uuidForPath(path);
        final String repoUrl = directory && ".git".equals(name) ? projectRepoUrl : null;
        return new FsEntry(name, absolutePath, directory, uuid, repoUrl);
    }
}
