package ac.uk.sussex.kn253.schema;

/**
 * Shared backend constants to avoid duplicated string literals in API and
 * repository services.
 *
 * <p>
 * Keep host/protocol/path policy constants here so future repository-host
 * support (e.g. GitLab/Bitbucket/self-hosted forge links) can be added in
 * service logic without scattering literals across resource/service classes.
 */
public final class BackendSupport {

    private BackendSupport() {
    }

    public static final String DIR_GIT = ".git";

    public static final String REMOTE_NAME_ORIGIN = "origin";
    public static final String HOST_GITHUB = "github.com";
    public static final String PROTOCOL_HTTP = "http";
    public static final String PROTOCOL_HTTPS = "https";
    public static final String REGEX_SUFFIX_DOT_GIT = "\\.git$";

    public static final String GH_REPO_VIEW_COMMAND = "gh repo view";
    public static final String JSON_FIELD_NAME = "name";
    public static final String JSON_FIELD_DESCRIPTION = "description";

    public static final String ERROR_NO_BROWSABLE_GITHUB_REMOTE = "No browsable GitHub remote found";
    public static final String ERROR_NOT_A_DIRECTORY = "Not a directory";
    public static final String ERROR_FAILED_TO_LIST_DIRECTORY = "Failed to list directory";
    public static final String ERROR_MISSING_QUERY_PATH = "Missing query parameter: path";
}
