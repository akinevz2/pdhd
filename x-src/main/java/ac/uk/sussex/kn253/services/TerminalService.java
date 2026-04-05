package ac.uk.sussex.kn253.services;

import java.io.IOException;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TerminalService {

    private final Terminal terminal;

    public TerminalService() throws IOException {
        super();
        ensureLoggingManagerConfigured();
        this.terminal = createTerminal();
    }

    public Terminal getTerminal() {
        return terminal;
    }

    private Terminal createTerminal() throws IOException {
        if (System.console() == null) {
            return TerminalBuilder.builder()
                    .dumb(true)
                    .streams(System.in, System.out)
                    .build();
        }

        try {
            return TerminalBuilder.builder().system(true).build();
        } catch (final IOException ex) {
            return TerminalBuilder.builder()
                    .dumb(true)
                    .streams(System.in, System.out)
                    .build();
        }
    }

    private void ensureLoggingManagerConfigured() {
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        }
        if (System.getProperty("org.jboss.logging.provider") == null) {
            System.setProperty("org.jboss.logging.provider", "jboss");
        }
    }
}