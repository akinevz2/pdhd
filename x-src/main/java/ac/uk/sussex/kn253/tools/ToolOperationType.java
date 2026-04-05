package ac.uk.sussex.kn253.tools;

/**
 * Classifies each tool macro by the kind of operation it represents.
 * Used for analytics, telemetry grouping, and observability.
 */
public enum ToolOperationType {

    /** Filesystem navigation, path resolution, and git discovery. */
    EXPLORE,

    /** Reading file content from a project directory. */
    READ,

    /** Writing files and creating structured project artefacts. */
    WRITE,

    /**
     * Session introspection, project manifests, knowledge recall, and embeddings.
     */
    INTROSPECT
}
