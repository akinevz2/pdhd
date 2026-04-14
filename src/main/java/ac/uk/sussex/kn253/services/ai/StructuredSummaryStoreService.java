package ac.uk.sussex.kn253.services.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.repository.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class StructuredSummaryStoreService {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_TARGET_PATH_LENGTH = 512;
    private static final int MAX_KNOWLEDGE_KEY_LENGTH = 200;

    public record StructuredSummaryPayload(String purpose, List<String> keyComponents, List<String> dependencies) {
    }

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public StructuredSummary upsert(final ProjectFolder project, final SummaryType summaryType, final String targetPath,
            final StructuredSummaryPayload payload) {
        Objects.requireNonNull(project, "project must not be null");
        Objects.requireNonNull(summaryType, "summaryType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        final String normalizedTargetPath = normalizeTargetPath(targetPath);
        final StructuredSummaryPayload normalizedPayload = normalizePayload(payload);
        final String keyComponentsJson = writeJson(normalizedPayload.keyComponents());
        final String dependenciesJson = writeJson(normalizedPayload.dependencies());
        final String contentHash = computeContentHash(summaryType, normalizedTargetPath, normalizedPayload);
        final String knowledgeKey = buildKnowledgeKey(summaryType, normalizedTargetPath);

        StructuredSummary summary = StructuredSummary.findByProjectTypeAndPath(project, summaryType,
                normalizedTargetPath);
        final Instant now = Instant.now();
        final boolean changed = summary == null || !contentHash.equals(summary.getContentHash());

        if (summary == null) {
            summary = new StructuredSummary();
            summary.setProject(project);
            summary.setSummaryType(summaryType);
            summary.setTargetPath(normalizedTargetPath);
            summary.setCreatedAt(now);
        }

        if (changed) {
            summary.setPurpose(normalizedPayload.purpose());
            summary.setKeyComponentsJson(keyComponentsJson);
            summary.setDependenciesJson(dependenciesJson);
            summary.setContentHash(contentHash);
            summary.setUpdatedAt(now);
            summary.setKnowledgeRefKey(knowledgeKey);
            summary.persist();
        }

        upsertProjectKnowledge(project, knowledgeKey, summaryType, normalizedTargetPath, normalizedPayload, contentHash,
                changed, now);
        return summary;
    }

    private void upsertProjectKnowledge(final ProjectFolder project, final String knowledgeKey,
            final SummaryType summaryType,
            final String targetPath, final StructuredSummaryPayload payload, final String contentHash,
            final boolean changed, final Instant now) {
        ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project, knowledgeKey);
        if (knowledge == null) {
            knowledge = new ProjectKnowledge();
            knowledge.setProject(project);
            knowledge.setKey(knowledgeKey);
            knowledge.setCreatedAt(now);
        }

        if (changed || knowledge.getJsonContent() == null || knowledge.getJsonContent().isBlank()) {
            knowledge.setJsonContent(writeJson(buildKnowledgePayload(summaryType, targetPath, payload, contentHash)));
            knowledge.setUpdatedAt(now);
            knowledge.persist();
        }
    }

    private Map<String, Object> buildKnowledgePayload(final SummaryType summaryType, final String targetPath,
            final StructuredSummaryPayload payload, final String contentHash) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", SCHEMA_VERSION);
        data.put("summaryType", summaryType.name());
        data.put("targetPath", targetPath);
        data.put("purpose", payload.purpose());
        data.put("keyComponents", payload.keyComponents());
        data.put("dependencies", payload.dependencies());
        data.put("contentHash", contentHash);
        return data;
    }

    private String normalizeTargetPath(final String targetPath) {
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("targetPath must not be blank");
        }
        final String normalized = targetPath.trim().replace('\\', '/');
        if (normalized.length() > MAX_TARGET_PATH_LENGTH) {
            throw new IllegalArgumentException("targetPath must be <= " + MAX_TARGET_PATH_LENGTH + " characters");
        }
        return normalized;
    }

    private StructuredSummaryPayload normalizePayload(final StructuredSummaryPayload payload) {
        final String purpose = payload.purpose() == null ? "" : payload.purpose().trim();
        if (purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        final List<String> keyComponents = sanitizeList(payload.keyComponents());
        final List<String> dependencies = sanitizeList(payload.dependencies());
        return new StructuredSummaryPayload(purpose, keyComponents, dependencies);
    }

    private List<String> sanitizeList(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private String buildKnowledgeKey(final SummaryType summaryType, final String targetPath) {
        final String prefix = "structured-summary:" + summaryType.name().toLowerCase(Locale.ROOT) + ":";
        final String fullKey = prefix + targetPath;
        if (fullKey.length() <= MAX_KNOWLEDGE_KEY_LENGTH) {
            return fullKey;
        }
        return prefix + "sha256:" + sha256(targetPath);
    }

    private String computeContentHash(final SummaryType summaryType, final String targetPath,
            final StructuredSummaryPayload payload) {
        final Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("summaryType", summaryType.name());
        canonical.put("targetPath", targetPath);
        canonical.put("purpose", payload.purpose());
        canonical.put("keyComponents", payload.keyComponents());
        canonical.put("dependencies", payload.dependencies());
        return sha256(writeJson(canonical));
    }

    private String writeJson(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (final JsonProcessingException e) {
            throw new IllegalStateException("Failed to write JSON", e);
        }
    }

    private String sha256(final String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (final byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
