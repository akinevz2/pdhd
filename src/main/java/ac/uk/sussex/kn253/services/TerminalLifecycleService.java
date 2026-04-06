package ac.uk.sussex.kn253.services;

import java.io.IOException;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@ApplicationScoped
public class TerminalLifecycleService {

    private Terminal mainTerminal;

    @Produces
    @Named("mainTerminal")
    public synchronized Terminal mainTerminal() {
        if (mainTerminal == null) {
            try {
                mainTerminal = TerminalBuilder.builder().dumb(true).system(true).build();
            } catch (final IOException e) {
                throw new IllegalStateException("Failed to initialize terminal", e);
            }
        }
        return mainTerminal;
    }

    @PreDestroy
    void closeTerminal() {
        if (mainTerminal == null) {
            return;
        }
        try {
            mainTerminal.close();
        } catch (final IOException ignored) {
            // Best-effort shutdown cleanup.
        }
    }
}
