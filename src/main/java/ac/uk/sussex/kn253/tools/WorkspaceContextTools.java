package ac.uk.sussex.kn253.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import ac.uk.sussex.kn253.services.CwdService;
import ac.uk.sussex.kn253.services.TelemetryService;
import ac.uk.sussex.kn253.support.ToolSupport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class WorkspaceContextTools {

    @Inject
    CwdService cwdService;

    @Inject
    TelemetryService telemetryService;

    @Tool("Get the backend current working directory as an absolute path.")
    public String getCurrentWorkingDirectory() {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        try {
            result = cwdService.getCurrentWorkingDirectory().toString();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error getting working directory: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "getCurrentWorkingDirectory",
                        ToolSupport.MODULE_WORKSPACE,
                        "",
                        result,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        false);
            }
        }
    }

    @Tool("List the root directories of currently open projects.")
    @Transactional
    public List<String> getOpenProjectDirectories() {
        final long started = System.nanoTime();
        List<String> result = null;
        String errorClass = null;
        try {
            result = cwdService.getOpenProjectDirectories();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            return List.of("Error listing project directories: " + e.getMessage());
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "getOpenProjectDirectories",
                        ToolSupport.MODULE_WORKSPACE,
                        "",
                        result != null ? result.toString() : null,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        false);
            }
        }
    }

    @Tool(name = "listDirectoryContents", value = {
            "List direct files and folders from a directory path. If blank, use the current working directory."
    })
    public String listDirectoryContents(
            @P("Directory path to list. If blank, current working directory is used.") final String directoryPath) {
        final long started = System.nanoTime();
        String result = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        try {
            final Path target = (directoryPath == null || directoryPath.isBlank())
                    ? cwdService.getCurrentWorkingDirectory().toAbsolutePath().normalize()
                    : Path.of(directoryPath).toAbsolutePath().normalize();

            if (!Files.exists(target)) {
                argumentValidationFailure = true;
                errorClass = IllegalArgumentException.class.getName();
                result = "Error listing directory: path does not exist: " + target;
                return result;
            }
            if (!Files.isDirectory(target)) {
                argumentValidationFailure = true;
                errorClass = IllegalArgumentException.class.getName();
                result = "Error listing directory: not a directory: " + target;
                return result;
            }

            try (var stream = Files.list(target)) {
                final List<String> entries = stream
                        .map(path -> (Files.isDirectory(path) ? "[D] " : "[F] ") + path.getFileName())
                        .sorted()
                        .collect(Collectors.toList());

                result = "Directory: " + target + "\n" + String.join("\n", entries);
                return result;
            }
        } catch (final IOException e) {
            errorClass = e.getClass().getName();
            result = "Error listing directory: " + e.getMessage();
            return result;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            result = "Error listing directory: " + e.getMessage();
            return result;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        "listDirectoryContents",
                        ToolSupport.MODULE_WORKSPACE,
                        directoryPath,
                        result,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }
}
