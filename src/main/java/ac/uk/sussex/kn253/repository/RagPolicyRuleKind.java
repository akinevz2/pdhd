package ac.uk.sussex.kn253.repository;

/**
 * Rule categories used by the RAG policy table.
 */
public enum RagPolicyRuleKind {
    MIME_BY_EXTENSION,
    LANGUAGE_BY_EXTENSION,
    IGNORABLE_FOLDER_NAME,
    MARKDOWN_EXTENSION,
    MARKDOWN_PREFIX
}
