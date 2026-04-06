package ac.uk.sussex.kn253.menu;

import java.util.Objects;

import org.jline.terminal.Terminal;

record AssistantViewport(Terminal terminal) {

    private static final int MIN_RENDER_WIDTH = 80;
    private static final int MIN_RENDER_ROWS = 1;
    private static final int WIDTH_PADDING = 2;
    private static final int HEIGHT_PADDING = 12;

    AssistantViewport {
        Objects.requireNonNull(terminal, "terminal");
        if (contentWidthFor(terminal) < MIN_RENDER_WIDTH) {
            throw new IllegalStateException(
                    "Assistant viewport requires at least " + MIN_RENDER_WIDTH + " columns of render width");
        }
        if (bodyHeightFor(terminal) < MIN_RENDER_ROWS) {
            throw new IllegalStateException(
                    "Assistant viewport requires at least " + MIN_RENDER_ROWS + " renderable row(s)");
        }
    }

    int width() {
        return widthFor(terminal);
    }

    int contentWidth() {
        return contentWidthFor(terminal);
    }

    int bodyHeight() {
        return bodyHeightFor(terminal);
    }

    private static int widthFor(final Terminal terminal) {
        return terminal.getWidth() - WIDTH_PADDING;
    }

    private static int contentWidthFor(final Terminal terminal) {
        return widthFor(terminal);
    }

    private static int bodyHeightFor(final Terminal terminal) {
        return terminal.getHeight() - HEIGHT_PADDING;
    }
}
