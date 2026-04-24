package ac.uk.sussex.kn253.services.ai;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.repository.*;
import ac.uk.sussex.kn253.services.TelemetryService;
import ac.uk.sussex.kn253.support.ToolSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FileSummarisationPipelineService {

    private static final Logger LOG = Logger.getLogger(FileSummarisationPipelineService.class);
    private static final String BLANK_SUMMARY_FALLBACK = "No summary content was returned by the summarisation step.";
    private static final String EMPTY_PURPOSE_FALLBACK = "Summary payload did not include a purpose; using fallback summary text.";

    private final AtomicLong parseSuccessCount = new AtomicLong();
    private final AtomicLong parseFallbackCount = new AtomicLong();

    @Inject
    FileSummarisationSubagent fileSummarisationSubagent;

    @Inject
    StructuredSummaryStoreService structuredSummaryStoreService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TelemetryService telemetryService;

    public record FolderSummaryResult(StructuredSummary summary, String fallbackReason) {
    }

    @Transactional
    public StructuredSummary summariseFileAndStore(final ProjectFolder project, final String filePath,
            final String fileContents) {
        final String rawSummary = fileSummarisationSubagent.summarise(fileContents, filePath);
        final ParsedPayload parsed = parsePayload(rawSummary);
        recordParseTelemetry("file", filePath, parsed, rawSummary);
        return structuredSummaryStoreService.upsert(project, SummaryType.FILE, filePath, parsed.payload());
    }

    @Transactional
    public StructuredSummary summariseFolderAndStore(final ProjectFolder project, final String folderPath,
            final String folderSummaries) {
        return summariseFolderAndStoreWithMetadata(project, folderPath, folderSummaries).summary();
    }

    @Transactional
    public FolderSummaryResult summariseFolderAndStoreWithMetadata(final ProjectFolder project, final String folderPath,
            final String folderSummaries) {
        final String rawSummary = fileSummarisationSubagent.summariseFolder(folderSummaries, folderPath);
        final ParsedPayload parsed = parsePayload(rawSummary);
        recordParseTelemetry("folder", folderPath, parsed, rawSummary);
        final StructuredSummary summary = structuredSummaryStoreService.upsert(project, SummaryType.FOLDER, folderPath,
                parsed.payload());
        return new FolderSummaryResult(summary, parsed.fallbackReason());
    }

    private ParsedPayload parsePayload(final String rawSummary) {
        final String fallbackPurpose = rawSummary == null ? "" : rawSummary.trim();
        if (fallbackPurpose.isBlank()) {
            parseFallbackCount.incrementAndGet();
            return new ParsedPayload(
                    new StructuredSummaryStoreService.StructuredSummaryPayload(BLANK_SUMMARY_FALLBACK, List.of(),
                            List.of()),
                    "blank_output_fallback",
                    "Summary output was blank; a deterministic fallback summary was stored.",
                    true);
        }

        try {
            final JsonNode root = objectMapper.readTree(rawSummary);
            if (root == null || !root.isObject()) {
                parseFallbackCount.incrementAndGet();
                return new ParsedPayload(
                        new StructuredSummaryStoreService.StructuredSummaryPayload(fallbackPurpose, List.of(),
                                List.of()),
                        "non_object_payload_fallback",
                        "Summary output was not a JSON object; raw summary text was used as fallback.",
                        true);
            }

            final String purpose = readText(root.get("purpose"), fallbackPurpose);
            final List<String> keyComponents = readStringArray(root.get("keyComponents"));
            final List<String> dependencies = readStringArray(root.get("dependencies"));
            parseSuccessCount.incrementAndGet();

            final String fallbackReason = purpose.equals(fallbackPurpose)
                    ? "Summary payload omitted a usable purpose; fallback summary text was used for purpose."
                    : null;
            final boolean usedFallback = fallbackReason != null;
            if (usedFallback) {
                parseFallbackCount.incrementAndGet();
            }

            return new ParsedPayload(
                    new StructuredSummaryStoreService.StructuredSummaryPayload(
                            purpose.isBlank() ? EMPTY_PURPOSE_FALLBACK : purpose,
                            keyComponents,
                            dependencies),
                    usedFallback ? "json_payload_with_fallback" : "json_payload",
                    fallbackReason,
                    usedFallback);
        } catch (final Exception ignored) {
            parseFallbackCount.incrementAndGet();
            return new ParsedPayload(
                    new StructuredSummaryStoreService.StructuredSummaryPayload(fallbackPurpose, List.of(), List.of()),
                    "invalid_json_fallback",
                    "Summary output was not valid JSON; raw summary text was used as fallback.",
                    true);
        }
    }

    private void recordParseTelemetry(final String summaryType,
            final String targetPath,
            final ParsedPayload parsed,
            final String rawSummary) {
        final long successes = parseSuccessCount.get();
        final long fallbacks = parseFallbackCount.get();
        final String inputPayload = "summaryType=" + summaryType
                + ";targetPath=" + (targetPath == null ? "" : targetPath)
                + ";rawLength=" + (rawSummary == null ? 0 : rawSummary.length());
        final String outputPayload = "mode=" + parsed.parseMode()
                + ";fallbackReason=" + (parsed.fallbackReason() == null ? "" : parsed.fallbackReason())
                + ";successCount=" + successes
                + ";fallbackCount=" + fallbacks;
        final String errorClass = parsed.usedFallback() ? "SummaryParseFallback" : null;

        telemetryService.recordToolUse(
                "summary_parse_payload",
                ToolSupport.MODULE_SUMMARY,
                inputPayload,
                outputPayload,
                0L,
                errorClass,
                false);

        if (parsed.usedFallback()) {
            LOG.warnf(
                    "Summary parse fallback (%s) for %s: mode=%s fallbackReason=%s (success=%d fallback=%d)",
                    summaryType.toUpperCase(Locale.ROOT),
                    targetPath,
                    parsed.parseMode(),
                    parsed.fallbackReason(),
                    successes,
                    fallbacks);
        }
    }

    private record ParsedPayload(
            StructuredSummaryStoreService.StructuredSummaryPayload payload,
            String parseMode,
            String fallbackReason,
            boolean usedFallback) {
    }

    private String readText(final JsonNode node, final String fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        final String text = node.asText("").trim();
        return text.isBlank() ? fallback : text;
    }

    private List<String> readStringArray(final JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        final List<String> values = new ArrayList<>();
        for (final JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            final String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }
}
