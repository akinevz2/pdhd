package ac.uk.sussex.kn253.commands;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import picocli.CommandLine.Command;

@Unremovable
@ApplicationScoped
@Command(name = "webui", description = "Open the local web UI")
public class WebUiCommand implements Runnable {

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int httpPort;

    @Override
    public void run() {
        final String url = "http://localhost:" + httpPort;
        System.out.println("\nWeb UI available at: " + url);

        try {
            final String os = System.getProperty("os.name", "").toLowerCase();
            final ProcessBuilder pb;
            if (os.contains("linux")) {
                pb = new ProcessBuilder("xdg-open", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("cmd", "/c", "start", url);
            }
            pb.inheritIO().start();
        } catch (final Exception e) {
            // Browser launch is best-effort; the URL is already printed above.
        }
    }
}
