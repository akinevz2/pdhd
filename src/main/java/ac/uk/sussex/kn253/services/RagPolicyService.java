package ac.uk.sussex.kn253.services;

import java.util.*;

import ac.uk.sussex.kn253.repository.RagPolicyRule;
import ac.uk.sussex.kn253.repository.RagPolicyRuleKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * DB-backed policy service for extension/mimetype/language/folder-ignore rules.
 */
@ApplicationScoped
public class RagPolicyService {

    private static final String DEFAULT_MIME_TYPE = "text/plain";
    private static final String DEFAULT_LANGUAGE = "plaintext";

    @Transactional
    public String resolveMimeType(final String fileName) {
        ensureDefaults();
        if (fileName == null || fileName.isBlank()) {
            return DEFAULT_MIME_TYPE;
        }

        final String extension = normalizeExtension(fileName);
        if (extension == null) {
            return DEFAULT_MIME_TYPE;
        }

        final RagPolicyRule rule = RagPolicyRule
                .find("kind = ?1 and matcher = ?2 and enabled = true", RagPolicyRuleKind.MIME_BY_EXTENSION, extension)
                .firstResult();
        return rule == null || rule.getMappedValue() == null || rule.getMappedValue().isBlank()
                ? DEFAULT_MIME_TYPE
                : rule.getMappedValue();
    }

    @Transactional
    public String resolveLanguage(final String fileName) {
        ensureDefaults();
        if (fileName == null || fileName.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        final String extension = normalizeExtension(fileName);
        if (extension == null) {
            return DEFAULT_LANGUAGE;
        }

        final RagPolicyRule rule = RagPolicyRule.find(
                "kind = ?1 and matcher = ?2 and enabled = true",
                RagPolicyRuleKind.LANGUAGE_BY_EXTENSION,
                extension).firstResult();
        return rule == null || rule.getMappedValue() == null || rule.getMappedValue().isBlank()
                ? DEFAULT_LANGUAGE
                : rule.getMappedValue();
    }

    @Transactional
    public boolean isIgnorableFolderName(final String folderName) {
        ensureDefaults();
        if (folderName == null || folderName.isBlank()) {
            return false;
        }
        final String normalized = folderName.toLowerCase(Locale.ROOT).trim();
        return RagPolicyRule
                .find("kind = ?1 and matcher = ?2 and enabled = true", RagPolicyRuleKind.IGNORABLE_FOLDER_NAME,
                        normalized)
                .firstResult() != null;
    }

    @Transactional
    public boolean isMarkdownFileName(final String fileName) {
        ensureDefaults();
        if (fileName == null || fileName.isBlank()) {
            return false;
        }

        final String normalized = fileName.toLowerCase(Locale.ROOT).trim();
        final String extension = normalizeExtension(normalized);
        if (extension != null) {
            final RagPolicyRule extRule = RagPolicyRule.find(
                    "kind = ?1 and matcher = ?2 and enabled = true",
                    RagPolicyRuleKind.MARKDOWN_EXTENSION,
                    extension).firstResult();
            if (extRule != null) {
                return true;
            }
        }

        final List<RagPolicyRule> prefixRules = RagPolicyRule
                .list("kind = ?1 and enabled = true", RagPolicyRuleKind.MARKDOWN_PREFIX);
        for (final RagPolicyRule rule : prefixRules) {
            if (rule.getMatcher() != null && normalized.startsWith(rule.getMatcher())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeExtension(final String fileName) {
        final String normalized = fileName.toLowerCase(Locale.ROOT).trim();
        final int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return null;
        }
        return normalized.substring(dot + 1);
    }

    private void ensureDefaults() {
        seedDefaults(RagPolicyRuleKind.IGNORABLE_FOLDER_NAME, mapOf(
                ".git", "1",
                ".idea", "1",
                ".vscode", "1",
                "node_modules", "1",
                "target", "1",
                "build", "1",
                "dist", "1"));

        seedDefaults(RagPolicyRuleKind.MARKDOWN_EXTENSION, mapOf(
                "md", "1",
                "markdown", "1"));

        seedDefaults(RagPolicyRuleKind.MARKDOWN_PREFIX, mapOf(
                "readme", "1"));

        seedDefaults(RagPolicyRuleKind.MIME_BY_EXTENSION, mapOf(
                "pdf", "application/pdf",
                "jpg", "image/jpeg",
                "jpeg", "image/jpeg",
                "png", "image/png",
                "gif", "image/gif",
                "svg", "image/svg+xml",
                "json", "application/json",
                "xml", "application/xml",
                "html", "text/html",
                "htm", "text/html",
                "css", "text/css",
                "js", "application/javascript",
                "jsx", "application/javascript",
                "ts", "application/typescript",
                "tsx", "application/typescript",
                "java", "text/plain",
                "py", "text/plain",
                "md", "text/markdown",
                "markdown", "text/markdown"));

        seedDefaults(RagPolicyRuleKind.LANGUAGE_BY_EXTENSION, mapOf(
                "json", "json",
                "xml", "xml",
                "html", "html",
                "htm", "html",
                "css", "css",
                "js", "javascript",
                "jsx", "jsx",
                "ts", "typescript",
                "tsx", "tsx",
                "java", "java",
                "py", "python",
                "md", "markdown",
                "markdown", "markdown",
                "sql", "sql",
                "yaml", "yaml",
                "yml", "yaml",
                "properties", "properties",
                "sh", "bash",
                "gradle", "gradle",
                "pom", "xml"));
    }

    private void seedDefaults(final RagPolicyRuleKind kind, final Map<String, String> values) {
        for (final Map.Entry<String, String> entry : values.entrySet()) {
            final String matcher = entry.getKey().toLowerCase(Locale.ROOT);
            RagPolicyRule existing = RagPolicyRule.find(
                    "kind = ?1 and matcher = ?2",
                    kind,
                    matcher).firstResult();
            if (existing == null) {
                existing = new RagPolicyRule();
                existing.setKind(kind);
                existing.setMatcher(matcher);
                existing.setMappedValue(entry.getValue());
                existing.setEnabled(true);
                existing.persist();
            }
        }
    }

    private static Map<String, String> mapOf(final String... keyValues) {
        final Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
