package ac.uk.sussex.kn253.menu;

import java.util.List;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;

record OllamaPullStatusBar(String modelName, String statusText, long completed, long total, int terminalWidth) {

    private static final int MIN_BAR_WIDTH = 12;
    private static final int MAX_BAR_WIDTH = 40;

    List<AttributedString> render() {
        final int width = Math.max(terminalWidth, 40);
        final String header = fitRight(
                "Pulling " + safeModelName() + "  " + normalizeStatusText(statusText),
                width);
        final String progress = fitRight(buildProgressLine(width), width);

        return List.of(
                new AttributedStringBuilder().append(header).toAttributedString(),
                new AttributedStringBuilder().append(progress).toAttributedString());
    }

    private String buildProgressLine(final int width) {
        if (total <= 0L) {
            final int barWidth = Math.min(MAX_BAR_WIDTH, Math.max(MIN_BAR_WIDTH, width - 26));
            return buildBar(0.0d, barWidth) + " waiting for total size";
        }

        final double ratio = Math.min(1.0d, Math.max(0.0d, (double) completed / (double) total));
        final int percent = (int) Math.round(ratio * 100.0d);
        final String metrics = percent + "%  " + humanSize(completed) + " / " + humanSize(total);
        final int barWidth = Math.min(MAX_BAR_WIDTH, Math.max(MIN_BAR_WIDTH, width - metrics.length() - 3));
        return buildBar(ratio, barWidth) + " " + metrics;
    }

    private String buildBar(final double ratio, final int barWidth) {
        final int filled = Math.max(0, Math.min(barWidth, (int) Math.round(ratio * barWidth)));
        return "[" + "#".repeat(filled) + "-".repeat(Math.max(0, barWidth - filled)) + "]";
    }

    private String fitRight(final String value, final int width) {
        if (value.length() <= width) {
            return value;
        }
        if (width <= 3) {
            return value.substring(0, width);
        }
        return value.substring(0, width - 3) + "...";
    }

    private String safeModelName() {
        if (modelName == null || modelName.isBlank()) {
            return "<unknown>";
        }
        return modelName.trim();
    }

    private String normalizeStatusText(final String value) {
        if (value == null || value.isBlank()) {
            return "pulling";
        }
        return value.trim();
    }

    private String humanSize(final long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024.0d);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format("%.1f MB", bytes / (1024.0d * 1024.0d));
        }
        return String.format("%.1f GB", bytes / (1024.0d * 1024.0d * 1024.0d));
    }
}