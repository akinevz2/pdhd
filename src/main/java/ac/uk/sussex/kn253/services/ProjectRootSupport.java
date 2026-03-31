package ac.uk.sussex.kn253.services;

import java.nio.file.Files;
import java.nio.file.Path;

import ac.uk.sussex.kn253.model.Project;

/**
 * Shared project-root detection logic used by both the chat summarization
 * pipeline and the introspect tool layer.
 *
 * <p>
 * Combining database lookup with conventional marker-file detection means
 * callers do not need to duplicate either concern.
 * </p>
 */
public final class ProjectRootSupport {

    private ProjectRootSupport() {
    }

    /**
     * Returns {@code true} if {@code directory} is registered as a project root
     * in the database, or if it contains at least one standard project-root
     * marker file or directory.
     *
     * <p>
     * Panache availability is not guaranteed in plain unit tests; database lookup
     * failures are silently swallowed and fall back to marker-file detection.
     * </p>
     */
    public static boolean isProjectRootDirectory(final Path directory) {
        final Path normalized = directory.toAbsolutePath().normalize();
        try {
            final Project project = Project.find("directory", normalized.toString()).firstResult();
            if (project != null) {
                return true;
            }
        } catch (final RuntimeException ignored) {
            // Panache unavailable in plain unit tests; fall through to marker detection.
        }
        return hasProjectRootMarkers(normalized);
    }

    /**
     * Returns {@code true} if {@code directory} contains at least one
     * conventional project-root marker file or directory (e.g. {@code .git},
     * {@code pom.xml}, {@code package.json}).
     */
    public static boolean hasProjectRootMarkers(final Path directory) {
        // FIXME: ask the model for known marker files and cache them in the database
        // instead of hardcoding them here.
        return Files.isDirectory(directory.resolve(".git"))
                || Files.isRegularFile(directory.resolve("pom.xml"))
                || Files.isRegularFile(directory.resolve("build.gradle"))
                || Files.isRegularFile(directory.resolve("build.gradle.kts"))
                || Files.isRegularFile(directory.resolve("package.json"))
                || Files.isRegularFile(directory.resolve("Cargo.toml"))
                || Files.isRegularFile(directory.resolve("go.mod"))
                || Files.isRegularFile(directory.resolve("pyproject.toml"))
                || Files.isRegularFile(directory.resolve("requirements.txt"))
                || Files.isRegularFile(directory.resolve("setup.py"))
                || Files.isRegularFile(directory.resolve("Makefile"))
                || Files.isRegularFile(directory.resolve("composer.json"))
                || Files.isRegularFile(directory.resolve("Gemfile"));
    }
}
