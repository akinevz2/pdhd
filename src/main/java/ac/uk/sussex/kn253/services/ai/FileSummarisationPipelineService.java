package ac.uk.sussex.kn253.services.ai;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FileSummarisationPipelineService {

    @Inject
    FileSummarisationSubagent fileSummarisationSubagent;

    @Inject
    StructuredSummaryStoreService structuredSummaryStoreService;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public StructuredSummary summariseFileAndStore(final ProjectFolder project, final String filePath,
            final String fileContents) {
        final String rawSummary = fileSummarisationSubagent.summarise(fileContents, filePath);
        final StructuredSummaryStoreService.StructuredSummaryPayload payload = parsePayload(rawSummary);
        return structuredSummaryStoreService.upsert(project, SummaryType.FILE, filePath, payload);
    }

    @Transactional
    public StructuredSummary summariseFolderAndStore(final ProjectFolder project, final String folderPath,
            final String folderSummaries) {
        final String rawSummary = fileSummarisationSubagent.summariseFolder(folderSummaries, folderPath);
        final StructuredSummaryStoreService.StructuredSummaryPayload payload = parsePayload(rawSummary);
        return structuredSummaryStoreService.upsert(project, SummaryType.FOLDER, folderPath, payload);
    }

    private StructuredSummaryStoreService.StructuredSummaryPayload parsePayload(final String rawSummary) {
        final String fallbackPurpose = rawSummary == null ? "" : rawSummary.trim();
        if (fallbackPurpose.isBlank()) {
            throw new IllegalArgumentException("Summary output was blank");
        }

        try {
            final JsonNode root = objectMapper.readTree(rawSummary);
            if (root == null || !root.isObject()) {
                return new StructuredSummaryStoreService.StructuredSummaryPayload(fallbackPurpose, List.of(),
                        List.of());
            }

            final String purpose = readText(root.get("purpose"), fallbackPurpose);
            final List<String> keyComponents = readStringArray(root.get("keyComponents"));
            final List<String> dependencies = readStringArray(root.get("dependencies"));
            return new StructuredSummaryStoreService.StructuredSummaryPayload(purpose, keyComponents, dependencies);
        } catch (final Exception ignored) {
            return new StructuredSummaryStoreService.StructuredSummaryPayload(fallbackPurpose, List.of(), List.of());
        }
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
