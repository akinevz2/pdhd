package ac.uk.sussex.kn253.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.services.CwdService;
import ac.uk.sussex.kn253.services.TelemetryService;
import ac.uk.sussex.kn253.support.ToolSupport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ReadFileTools {

    @Inject
    CwdService cwdService;

    @Inject
    TelemetryService telemetryService;

    @Tool(name = "readFile", value = {
            "Reads and returns the complete raw text content of a file.",
            " Call this tool for any request that requires knowing what a file actually contains: source code, configuration, README text, build files, etc.",
            " Does NOT list directory contents — use listDirectoryContents or listFilesRecursive for that.",
            " On success, returns the full file content as a plain string.",
            " On failure or access denial, returns a string starting with 'Error reading file:', 'Access denied:', or 'Error: path must not be blank'." })
    @Transactional
    public String readFile(
            @P("Path to the file to read. Accepts absolute paths or relative paths resolved against any open project root. Must not be blank. If a relative path matches files in multiple open projects, the first match is returned.") final String path) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;
        try {
            if (path == null || path.isBlank()) {
                argumentValidationFailure = true;
                result = "Error: path must not be blank";
                return result;
            }
            final Path normalized = resolvePathInsideAllowedRoots(path);
            result = Files.readString(normalized);
            return result;
        } catch (final SecurityException e) {
            errorClass = e.getClass().getName();
            result = "Access denied: " + e.getMessage();
            return result;
        } catch (final IOException e) {
            errorClass = e.getClass().getName();
            result = "Error reading file: " + e.getMessage();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error reading file: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "readFile",
                        ToolSupport.MODULE_READ_FILE,
                        path,
                        result,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    private Path resolvePathInsideAllowedRoots(final String requestedPath) {
        final Path rawPath = Path.of(requestedPath);
        final List<Path> allowedRoots = getAllowedRoots();

        if (rawPath.isAbsolute()) {
            final Path normalized = rawPath.toAbsolutePath().normalize();
            requireInsideAllowedRoots(normalized, allowedRoots);
            return normalized;
        }

        Path firstScopedCandidate = null;
        for (final Path root : allowedRoots) {
            final Path candidate = root.resolve(rawPath).toAbsolutePath().normalize();
            if (!candidate.startsWith(root)) {
                continue;
            }
            if (firstScopedCandidate == null) {
                firstScopedCandidate = candidate;
            }
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        if (firstScopedCandidate != null) {
            return firstScopedCandidate;
        }

        throw new SecurityException(
                "Access denied: " + rawPath + " is not within any currently open project");
    }

    private void requireInsideAllowedRoots(final Path filePath, final List<Path> allowedRoots) {
        final boolean inside = allowedRoots.stream().anyMatch(filePath::startsWith);
        if (!inside) {
            throw new SecurityException(
                    "Access denied: " + filePath + " is not within any currently open project");
        }
    }

    private List<Path> getAllowedRoots() {
        final List<Path> roots = new ArrayList<>();
        if (cwdService != null && cwdService.getCurrentWorkingDirectory() != null) {
            roots.add(cwdService.getCurrentWorkingDirectory().toAbsolutePath().normalize());
        }
        final List<ProjectFolder> openProjects = ProjectFolder.list("loaded", true);
        for (final ProjectFolder project : openProjects) {
            if (project == null || project.getDirectory() == null || project.getDirectory().isBlank()) {
                continue;
            }
            roots.add(Path.of(project.getDirectory()).toAbsolutePath().normalize());
        }
        return roots.stream().distinct().toList();
    }
}
