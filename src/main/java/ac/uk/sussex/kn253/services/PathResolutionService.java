package ac.uk.sussex.kn253.services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Handles path resolution, UUID generation for paths, and file system
 * traversal operations.
 */
@ApplicationScoped
public class PathResolutionService {

    public String uuidForPath(final Path path) {
        final String normalized = path.toAbsolutePath().normalize().toString();
        return UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public Path resolvePathByUuid(final Path root, final String entryUuid) {
        if (entryUuid == null || entryUuid.isBlank()) {
            return root;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> uuidForPath(path).equals(entryUuid))
                    .findFirst()
                    .orElse(null);
        } catch (final Exception e) {
            return null;
        }
    }

    public String normalizeRelativePath(final Path root, final Path path) {
        if (root == null || path == null) {
            return "";
        }
        try {
            final Path relative = root.relativize(path);
            final String pathStr = relative.toString().replace("\\", "/");
            return ".".equals(pathStr) || pathStr.isBlank() ? "." : pathStr;
        } catch (final Exception e) {
            return path.toString();
        }
    }

    public List<Path> listDirectoryEntries(final Path directory) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> children = Files.list(directory)) {
            return children
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (final Exception e) {
            return List.of();
        }
    }
}
