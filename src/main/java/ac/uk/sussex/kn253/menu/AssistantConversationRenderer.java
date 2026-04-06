package ac.uk.sussex.kn253.menu;

import java.io.*;
import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;

interface AssistantConversationRenderer {

    record RenderedTranscriptLine(String text) {
    }

    record RenderLinesData(List<AssistantTranscript.TranscriptEntry> entries, int contentWidth) {
    }

    record RenderData(
            List<RenderedTranscriptLine> renderedLines,
            String statusLine,
            AssistantViewport viewport,
            int start,
            int end,
            int scrollOffsetFromBottom,
            int maxOffset) {
    }

    List<RenderedTranscriptLine> renderLines(RenderLinesData data);

    AttributedStringBuilder render(RenderData data);
}

final class DefaultAssistantConversationRenderer implements AssistantConversationRenderer {

    private final Terminal terminal;

    DefaultAssistantConversationRenderer(final InputStream inputStream, final OutputStream outputStream) {
        try {
            this.terminal = TerminalBuilder.builder()
                    .dumb(true)
                    .system(false)
                    .streams(inputStream, outputStream)
                    .build();
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to initialize renderer terminal", e);
        }
    }

    @Override
    public List<RenderedTranscriptLine> renderLines(final RenderLinesData data) {
        terminal.flush();
        throw new UnsupportedOperationException("Default renderer implementation has been carved out");
    }

    @Override
    public AttributedStringBuilder render(final RenderData data) {
        terminal.flush();
        throw new UnsupportedOperationException("Default renderer implementation has been carved out");
    }
}