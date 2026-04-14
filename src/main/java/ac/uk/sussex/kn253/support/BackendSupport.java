package ac.uk.sussex.kn253.support;

/**
 * Centralised host-name, protocol, URL, and policy constants for backend
 * service integrations.
 *
 * <p>
 * Rules (from recommendations-for-implementation §9):
 * <ul>
 * <li>Host-specific rules live here, not in controllers or tools.</li>
 * <li>Host policy constants for any new forge (GitLab, Bitbucket, …) are
 * added here first.</li>
 * <li>URL validation and host selection remain in the relevant service layer,
 * which reads these constants.</li>
 * </ul>
 */
public final class BackendSupport {

    private BackendSupport() {
    }

    // ── Git forge host names ──────────────────────────────────────────────────

    /** Canonical hostname for GitHub. Used by {@code Origin#isGithub()}. */
    public static final String GITHUB_HOST = "github.com";

    // ── Web-search service ────────────────────────────────────────────────────

    /** Config property key that overrides the DuckDuckGo base URL. */
    public static final String WEB_SEARCH_BASE_URL_PROPERTY = "pdhd.web-search.base-url";

    /** Default base URL for the DuckDuckGo Lite scraper. */
    public static final String DUCKDUCKGO_LITE_BASE_URL = "https://lite.duckduckgo.com/lite/";

    // ── GitHub CLI process ────────────────────────────────────────────────────

    /** Maximum seconds to wait for a {@code gh} CLI subprocess to complete. */
    public static final int GH_CLI_TIMEOUT_SECONDS = 10;

    // ── Schema management ─────────────────────────────────────────────────────

    /**
     * The only acceptable Hibernate schema management strategy in environments
     * where telemetry history must be preserved.
     *
     * <p>
     * Any migration that would switch this to {@code drop-and-create} in
     * production is a blocking change and must be reviewed before merging.
     */
    public static final String SAFE_SCHEMA_STRATEGY = "update";
}
