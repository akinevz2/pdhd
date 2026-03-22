package ac.uk.sussex.kn253.services;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WebUiService {

    public void launch() {
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
