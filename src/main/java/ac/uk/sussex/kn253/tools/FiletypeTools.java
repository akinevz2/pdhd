package ac.uk.sussex.kn253.tools;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FiletypeTools {

    @Tool
    public String bashFile(final String filePath) {
        final var args = new String[] { "file", "--brief", "--mime-type", filePath };
        try {
            final Process process = new ProcessBuilder(args).start();
            final String output = new String(process.getInputStream().readAllBytes()).trim();
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                final String errorOutput = new String(process.getErrorStream().readAllBytes()).trim();
                throw new RuntimeException(
                        "file command failed (`file` and `libmagic` packages might not be installed): " + errorOutput);
            }
            return output;
        } catch (final RuntimeException e) {
            return e.getMessage();
        } catch (final Exception e) {
            return "Error determining file type using bash: " + e.getMessage();
        }
    }
}
