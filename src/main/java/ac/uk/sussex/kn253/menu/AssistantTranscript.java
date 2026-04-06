package ac.uk.sussex.kn253.menu;

import java.util.ArrayList;
import java.util.List;

final class AssistantTranscript {

    record TranscriptEntry(String rolePrefix, String text) {
    }

    private final List<TranscriptEntry> entries = new ArrayList<>();
    private int scrollOffsetFromBottom;
    private String statusLine;

    void append(final String rolePrefix, final String text) {
        entries.add(new TranscriptEntry(
                rolePrefix == null ? "" : rolePrefix,
                text == null ? "" : text));
        scrollOffsetFromBottom = 0;
    }

    void setStatusLine(final String statusLine) {
        this.statusLine = statusLine;
    }

    String statusLine() {
        return statusLine;
    }

    List<TranscriptEntry> entries() {
        return List.copyOf(entries);
    }

    int scrollOffsetFromBottom() {
        return scrollOffsetFromBottom;
    }

    void setScrollOffsetFromBottom(final int scrollOffsetFromBottom) {
        this.scrollOffsetFromBottom = Math.max(0, scrollOffsetFromBottom);
    }

    void scrollUp(final int amount) {
        scrollOffsetFromBottom = scrollOffsetFromBottom + Math.max(1, amount);
    }

    void scrollDown(final int amount) {
        scrollOffsetFromBottom = Math.max(0, scrollOffsetFromBottom - Math.max(1, amount));
    }
}
