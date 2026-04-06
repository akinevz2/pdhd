package ac.uk.sussex.kn253.menu;

import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp.Capability;

public final class TerminalUi {

    private static final int DEFAULT_TERMINAL_HEIGHT = 24;

    private TerminalUi() {
    }

    public static void clearScreen(final Terminal terminal) {
        if (terminal == null) {
            return;
        }
        scrollViewport(terminal);
        terminal.puts(Capability.clear_screen);
        terminal.flush();
    }

    private static void scrollViewport(final Terminal terminal) {
        final int height = terminal.getHeight() > 0 ? terminal.getHeight() : DEFAULT_TERMINAL_HEIGHT;
        final var writer = terminal.writer();
        for (int i = 0; i < height; i++) {
            writer.println();
        }
        writer.flush();
    }
}
