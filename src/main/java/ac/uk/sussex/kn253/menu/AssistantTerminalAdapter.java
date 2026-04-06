package ac.uk.sussex.kn253.menu;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.jline.terminal.*;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.ColorPalette;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.NonBlockingReader;

final class AssistantTerminalAdapter implements Terminal {

    private final Terminal terminal;
    private final AssistantTranscript transcript;
    private final AssistantConversationRenderer renderer;
    private final PipedOutputStream inputForwarder;
    private final Terminal frameTerminal;

    AssistantTerminalAdapter(final Terminal terminal) {
        this.terminal = terminal;
        this.transcript = new AssistantTranscript();
        this.renderer = new DefaultAssistantConversationRenderer(
                new ByteArrayInputStream(new byte[0]),
                terminal.output());
        try {
            final PipedInputStream inputStream = new PipedInputStream();
            this.inputForwarder = new PipedOutputStream(inputStream);
            this.frameTerminal = TerminalBuilder.builder()
                    .dumb(true)
                    .system(false)
                    .streams(inputStream, new FrameOutputStream())
                    .build();
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to initialize assistant frame terminal", e);
        }
    }

    void showUserMessage(final String userRole, final String text) {
        transcript.setStatusLine(null);
        forwardInput(text);
        printLine(userRole, text);
    }

    void showAssistantMessage(final String assistantRole, final String text, final String statusLine) {
        transcript.setStatusLine(statusLine);
        printLine(assistantRole, text);
    }

    void showStatus(final String statusLine) {
        transcript.setStatusLine(statusLine);
        render();
    }

    void scrollUp(final int amount) {
        transcript.scrollUp(amount);
        render();
    }

    void scrollDown(final int amount) {
        transcript.scrollDown(amount);
        render();
    }

    void clearTransientStatusLine() {
        terminal.puts(org.jline.utils.InfoCmp.Capability.carriage_return);
        terminal.puts(org.jline.utils.InfoCmp.Capability.clr_eol);
        terminal.flush();
    }

    void render() {
        final AssistantViewport viewport = new AssistantViewport(terminal);
        final var renderedLines = renderer.renderLines(new AssistantConversationRenderer.RenderLinesData(
                transcript.entries(),
                viewport.contentWidth()));
        final int renderedLineCount = renderedLines.size();
        final int maxOffset = Math.max(0, renderedLineCount - viewport.bodyHeight());
        final int scrollOffset = Math.max(0, Math.min(transcript.scrollOffsetFromBottom(), maxOffset));
        final int start = Math.max(0, renderedLineCount - viewport.bodyHeight() - scrollOffset);
        final int end = Math.min(renderedLineCount, start + viewport.bodyHeight());
        transcript.setScrollOffsetFromBottom(scrollOffset);
        TerminalUi.clearScreen(terminal);
        terminal.writer().print(renderer.render(new AssistantConversationRenderer.RenderData(
                renderedLines,
                transcript.statusLine(),
                viewport,
                start,
                end,
                scrollOffset,
                maxOffset)).toAnsi(terminal) + System.lineSeparator());
        terminal.writer().flush();
    }

    // -- Terminal delegation --

    @Override
    public String getName() {
        return terminal.getName();
    }

    @Override
    public SignalHandler handle(final Signal signal, final SignalHandler handler) {
        return terminal.handle(signal, handler);
    }

    @Override
    public void raise(final Signal signal) {
        terminal.raise(signal);
    }

    @Override
    public NonBlockingReader reader() {
        return terminal.reader();
    }

    @Override
    public PrintWriter writer() {
        return terminal.writer();
    }

    @Override
    public Charset encoding() {
        return terminal.encoding();
    }

    @Override
    public InputStream input() {
        return terminal.input();
    }

    @Override
    public OutputStream output() {
        return terminal.output();
    }

    @Override
    public boolean canPauseResume() {
        return terminal.canPauseResume();
    }

    @Override
    public void pause() {
        terminal.pause();
    }

    @Override
    public void pause(final boolean wait) throws InterruptedException {
        terminal.pause(wait);
    }

    @Override
    public void resume() {
        terminal.resume();
    }

    @Override
    public boolean paused() {
        return terminal.paused();
    }

    @Override
    public Attributes enterRawMode() {
        return terminal.enterRawMode();
    }

    @Override
    public boolean echo() {
        return terminal.echo();
    }

    @Override
    public boolean echo(final boolean echo) {
        return terminal.echo(echo);
    }

    @Override
    public Attributes getAttributes() {
        return terminal.getAttributes();
    }

    @Override
    public void setAttributes(final Attributes attr) {
        terminal.setAttributes(attr);
    }

    @Override
    public Size getSize() {
        return terminal.getSize();
    }

    @Override
    public void setSize(final Size size) {
        terminal.setSize(size);
    }

    @Override
    public void flush() {
        terminal.flush();
    }

    @Override
    public String getType() {
        return terminal.getType();
    }

    @Override
    public boolean puts(final Capability capability, final Object... params) {
        return terminal.puts(capability, params);
    }

    @Override
    public boolean getBooleanCapability(final Capability capability) {
        return terminal.getBooleanCapability(capability);
    }

    @Override
    public Integer getNumericCapability(final Capability capability) {
        return terminal.getNumericCapability(capability);
    }

    @Override
    public String getStringCapability(final Capability capability) {
        return terminal.getStringCapability(capability);
    }

    @Override
    public Cursor getCursorPosition(final IntConsumer discarded) {
        return terminal.getCursorPosition(discarded);
    }

    @Override
    public boolean hasMouseSupport() {
        return terminal.hasMouseSupport();
    }

    @Override
    public boolean trackMouse(final MouseTracking tracking) {
        return terminal.trackMouse(tracking);
    }

    @Override
    public MouseTracking getCurrentMouseTracking() {
        return terminal.getCurrentMouseTracking();
    }

    @Override
    public MouseEvent readMouseEvent() {
        return terminal.readMouseEvent();
    }

    @Override
    public MouseEvent readMouseEvent(final IntSupplier supplier) {
        return terminal.readMouseEvent(supplier);
    }

    @Override
    public MouseEvent readMouseEvent(final String prefix) {
        return terminal.readMouseEvent(prefix);
    }

    @Override
    public MouseEvent readMouseEvent(final IntSupplier supplier, final String prefix) {
        return terminal.readMouseEvent(supplier, prefix);
    }

    @Override
    public boolean hasFocusSupport() {
        return terminal.hasFocusSupport();
    }

    @Override
    public boolean trackFocus(final boolean tracking) {
        return terminal.trackFocus(tracking);
    }

    @Override
    public ColorPalette getPalette() {
        return terminal.getPalette();
    }

    @Override
    public void close() {
        try {
            inputForwarder.close();
            frameTerminal.close();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to close assistant terminal adapter", e);
        }
    }

    private void forwardInput(final String input) {
        if (input == null) {
            return;
        }
        try {
            inputForwarder.write(input.getBytes(StandardCharsets.UTF_8));
            inputForwarder.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            inputForwarder.flush();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to forward assistant input", e);
        }
    }

    private void printLine(final String rolePrefix, final String text) {
        final var terminalWriter = frameTerminal.writer();
        terminalWriter.print(rolePrefix);
        terminalWriter.println(text == null ? "" : text);
        terminalWriter.flush();
    }

    private final class FrameOutputStream extends OutputStream {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void write(final int value) {
            final char c = (char) value;
            if (c == '\r') {
                return;
            }
            if (c == '\n') {
                flushBuffer();
                return;
            }
            buffer.append(c);
        }

        @Override
        public void flush() {
            flushBuffer();
        }

        private void flushBuffer() {
            if (buffer.length() == 0) {
                return;
            }
            transcript.append("", buffer.toString());
            buffer.setLength(0);
            render();
        }
    }
}
