package ac.uk.sussex.kn253.support;

/**
 * Canonical JSON payload key constants shared across the backend.
 *
 * <p>
 * Rules (from recommendations-for-implementation §9):
 * <ul>
 * <li>Use this class only for JSON key strings – field names that appear in
 * serialised payloads and are read by name (e.g. via Jackson
 * {@code JsonNode#path}).</li>
 * <li>Do <em>not</em> use this class for non-key values or policy constants;
 * use {@link ToolSupport} or {@link BackendSupport} for those.</li>
 * </ul>
 */
public final class SchemaKeys {

    private SchemaKeys() {
    }

    // ── GitHub CLI JSON fields ────────────────────────────────────────────────

    /** {@code gh repo view} output: repository name. */
    public static final String GH_NAME = "name";

    /** {@code gh repo view} output: repository description. */
    public static final String GH_DESCRIPTION = "description";

    /** {@code gh repo view} output: repository HTML URL. */
    public static final String GH_URL = "url";

    // ── API response envelope ─────────────────────────────────────────────────

    /**
     * Version tag included in structured API responses so consumers can detect
     * schema evolution without breaking changes.
     */
    public static final String SCHEMA_VERSION = "schemaVersion";

    // ── Project/remote URL responses ──────────────────────────────────────────

    /** Key for the remote repository URL returned from project endpoints. */
    public static final String REMOTE_URL = "remoteUrl";
}
