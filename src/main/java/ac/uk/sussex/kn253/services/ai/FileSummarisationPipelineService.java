package ac.uk.sussex.kn253.services.ai;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.AiToolCallException;
import ac.uk.sussex.kn253.repository.*;
import ac.uk.sussex.kn253.services.TelemetryService;
import ac.uk.sussex.kn253.support.ToolSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FileSummarisationPipelineService {

    private static final Logger LOG = Logger.getLogger(FileSummarisationPipelineService.class);
    private static final String EMPTY_PURPOSE_FALLBACK = "Summary payload did not include a purpose; using fallback summary text.";
    private static final String ERR_BLANK_SUMMARY_OUTPUT = "Model returned blank summarisation output";
    private static final String ERR_MALFORMED_SUMMARY_JSON = "Malformed JSON in summarisation model output";
    private static final String ERR_NON_OBJECT_SUMMARY_JSON = "Model returned non-object JSON in summarisation output";

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
        final String jsonPayload = extractJsonPayload(rawSummary);
        if (jsonPayload == null || jsonPayload.isBlank()) {
            throw new AiToolCallException(ERR_BLANK_SUMMARY_OUTPUT);
        }

        try {
            final JsonNode root = objectMapper.readTree(jsonPayload);
            if (root == null || !root.isObject()) {
                throw new AiToolCallException(ERR_NON_OBJECT_SUMMARY_JSON, null, rawSummary);
            }

            final String purpose = readText(root.get("purpose"));
            final List<String> keyComponents = readStringArray(root.get("keyComponents"));
            final List<String> dependencies = readStringArray(root.get("dependencies"));
            parseSuccessCount.incrementAndGet();

            final boolean usedFallbackPurpose = purpose.isBlank();
            final String fallbackReason = usedFallbackPurpose
                    ? "Summary payload omitted a usable purpose; fallback summary text was used for purpose."
                    : null;
            final boolean usedFallback = usedFallbackPurpose;
            if (usedFallback) {
                parseFallbackCount.incrementAndGet();
            }

            return new ParsedPayload(
                    new StructuredSummaryStoreService.StructuredSummaryPayload(
                            usedFallbackPurpose ? EMPTY_PURPOSE_FALLBACK : purpose,
                            keyComponents,
                            dependencies),
                    usedFallback ? "json_payload_with_fallback" : "json_payload",
                    fallbackReason,
                    usedFallback);
        } catch (final AiToolCallException e) {
            throw e;
        } catch (final Exception e) {
            throw new AiToolCallException(ERR_MALFORMED_SUMMARY_JSON, e, rawSummary);
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

    private String extractJsonPayload(final String rawSummary) {
        if (rawSummary == null) {
            return null;
        }

        final int firstBrace = rawSummary.indexOf('{');
        final int lastBrace = rawSummary.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return rawSummary.substring(firstBrace, lastBrace + 1).trim();
        }

        return rawSummary.trim();
    }

    private String readText(final JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        final String text = node.asText("").trim();
        return text;
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
