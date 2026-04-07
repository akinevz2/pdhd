package ac.uk.sussex.kn253.commands;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import picocli.CommandLine.Command;

@Unremovable
@ApplicationScoped
@Command(name = "webui", description = "Launch web UI")
public class WebUiCommand implements Runnable {

    @Override
    public void run() {
        final String port = System.getProperty("quarkus.http.port", "8080");
        final String url = "http://localhost:" + port;
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
