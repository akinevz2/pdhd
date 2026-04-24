package ac.uk.sussex.kn253.support;

/**
 * Shared constants for tool module identifiers and other non-key tool/API
 * values.
 *
 * <p>
 * Rules (from recommendations-for-implementation §9):
 * <ul>
 * <li>Use these values as the {@code moduleName} argument when recording
 * telemetry via {@link ac.uk.sussex.kn253.services.TelemetryService}.</li>
 * <li>Do <em>not</em> use this class for JSON payload keys – those belong in
 * {@link SchemaKeys}.</li>
 * <li>Do <em>not</em> use this class for service/host policy – those belong
 * in {@link BackendSupport}.</li>
 * </ul>
 */
public final class ToolSupport {

    private ToolSupport() {
    }

    // ── Telemetry module identifiers ──────────────────────────────────────────

    /** Module name used by {@code WorkspaceContextTools}. */
    public static final String MODULE_WORKSPACE = "WORKSPACE";

    /** Module name used by {@code ReadFileTools}. */
    public static final String MODULE_READ_FILE = "READ_FILE";

    /** Module name used by {@code WebSearchTools}. */
    public static final String MODULE_WEB_SEARCH = "WEB_SEARCH";

    /** Module name used by summary parsing and fallback telemetry. */
    public static final String MODULE_SUMMARY = "SUMMARY";

    /** Module name used by {@code GitMetadataTools}. */
    public static final String MODULE_GIT = "GIT";
}
