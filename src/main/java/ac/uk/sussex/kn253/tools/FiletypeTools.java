package ac.uk.sussex.kn253.tools;

import java.io.File;

import ac.uk.sussex.kn253.services.CwdService;
import ac.uk.sussex.kn253.services.TelemetryService;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FiletypeTools {

    @Inject
    CwdService cwdService;

    @Inject
    TelemetryService telemetryService;

    @Tool
    public String bashFile(final String filePath) {
        final long started = System.nanoTime();
        String outputPayload = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;

        final var args = new String[] { "file", "--brief", "--mime-type", filePath };
        try {
            if (filePath == null || filePath.isBlank()) {
                argumentValidationFailure = true;
                throw new IllegalArgumentException("filePath must not be blank");
            }
            final ProcessBuilder processBuilder = new ProcessBuilder(args);
            final String workingDirectory = cwdService == null
                    ? null
                    : cwdService.getCurrentWorkingDirectory();
            if (workingDirectory != null && !workingDirectory.isBlank()) {
                processBuilder.directory(new File(workingDirectory));
            }
            final Process process = processBuilder.start();
            final String output = new String(process.getInputStream().readAllBytes()).trim();
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                final String errorOutput = new String(process.getErrorStream().readAllBytes()).trim();
                throw new RuntimeException(
                        "file command failed (`file` and `libmagic` packages might not be installed): " + errorOutput);
            }
            outputPayload = output;
            return outputPayload;
        } catch (final RuntimeException e) {
            errorClass = e.getClass().getName();
            outputPayload = e.getMessage();
            return outputPayload;
        } catch (final Exception e) {
            errorClass = e.getClass().getName();
            outputPayload = "Error determining file type using bash: " + e.getMessage();
            return outputPayload;
        } finally {
            if (telemetryService != null) {
                final long durationNanos = Math.max(0L, System.nanoTime() - started);
                telemetryService.recordToolUse(
                        "bashFile",
                        "FILETYPE",
                        "filePath=" + filePath,
                        outputPayload,
                        durationNanos,
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }
}
