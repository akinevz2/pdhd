package ac.uk.sussex.kn253.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ac.uk.sussex.kn253.repository.ProjectFolder;
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
    TelemetryService telemetryService;

    @Tool("Read the contents of a file at the given absolute path. The file must be within a currently open project.")
    @Transactional
    public String readFile(@P("Absolute path to the file") final String path) {
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
            final Path normalized = Path.of(path).toAbsolutePath().normalize();
            requireInsideOpenProject(normalized);
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

    private void requireInsideOpenProject(final Path filePath) {
        final List<ProjectFolder> openProjects = ProjectFolder.list("loaded", true);
        final boolean inside = openProjects.stream()
                .map(p -> Path.of(p.getDirectory()))
                .anyMatch(filePath::startsWith);
        if (!inside) {
            throw new SecurityException(
                    "Access denied: " + filePath + " is not within any currently open project");
        }
    }
}
