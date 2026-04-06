package ac.uk.sussex.kn253.menu;

import java.io.Closeable;
import java.util.function.Consumer;

import org.jline.keymap.KeyMap;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;
import org.jline.terminal.MouseEvent;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp.Capability;

import ac.uk.sussex.kn253.events.ChatResizedEvent;

final class AssistantInputAdapter implements Closeable {

    private final LineReader lineReader;
    private final java.util.function.Supplier<String> promptSupplier;
    private final Runnable scrollUpAction;
    private final Runnable scrollDownAction;
    private final Consumer<ChatResizedEvent> resizedEventConsumer;
    private final Terminal.MouseTracking previousMouseTracking;
    private int lastWidth;
    private int lastHeight;

    AssistantInputAdapter(
            final LineReader lineReader,
            final java.util.function.Supplier<String> promptSupplier,
            final Runnable scrollUpAction,
            final Runnable scrollDownAction,
            final Consumer<ChatResizedEvent> resizedEventConsumer) {
        this.lineReader = lineReader;
        this.promptSupplier = promptSupplier;
        this.scrollUpAction = scrollUpAction;
        this.scrollDownAction = scrollDownAction;
        this.resizedEventConsumer = resizedEventConsumer;
        this.previousMouseTracking = lineReader.getTerminal().getCurrentMouseTracking();
        this.lastWidth = lineReader.getTerminal().getWidth();
        this.lastHeight = lineReader.getTerminal().getHeight();
        installMouseSupport();
    }

    String readInput() {
        notifyIfResized();
        return lineReader.readLine(promptSupplier.get());
    }

    @Override
    public void close() {
        final Terminal terminal = lineReader.getTerminal();
        terminal.trackMouse(previousMouseTracking == null ? Terminal.MouseTracking.Off : previousMouseTracking);
        terminal.flush();
    }

    private void installMouseSupport() {
        final Terminal terminal = lineReader.getTerminal();
        terminal.trackMouse(Terminal.MouseTracking.Button);
        final String mouseSequence = KeyMap.key(terminal, Capability.key_mouse);
        if (mouseSequence == null || mouseSequence.isBlank()) {
            return;
        }

        final var keyMap = lineReader.getKeyMaps().get(LineReader.MAIN);
        if (keyMap != null) {
            keyMap.bind(new Reference(LineReader.MOUSE), mouseSequence);
        }

        lineReader.getWidgets().put(LineReader.MOUSE, () -> {
            handleMouseEvent(lineReader.readMouseEvent());
            return true;
        });
    }

    private void handleMouseEvent(final MouseEvent event) {
        notifyIfResized();
        if (event == null || event.getButton() == null) {
            return;
        }
        switch (event.getButton()) {
            case WheelUp -> scrollUpAction.run();
            case WheelDown -> scrollDownAction.run();
            default -> {
            }
        }
    }

    private void notifyIfResized() {
        final Terminal terminal = lineReader.getTerminal();
        final int currentWidth = terminal.getWidth();
        final int currentHeight = terminal.getHeight();
        if (currentWidth == lastWidth && currentHeight == lastHeight) {
            return;
        }

        final ChatResizedEvent event = new ChatResizedEvent(lastWidth, lastHeight, currentWidth, currentHeight);
        lastWidth = currentWidth;
        lastHeight = currentHeight;
        if (resizedEventConsumer != null) {
            resizedEventConsumer.accept(event);
        }
    }
}
