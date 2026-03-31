package ac.uk.sussex.kn253.services.tools.macro.introspect;

public final class ManifestPromptSupport {

    private ManifestPromptSupport() {
    }

    public static final String READ_PROJECT_MANIFEST_ONLY_FOR_ROOTS = "read_project_manifest is only allowed for project root folders. Use read_folder_manifest for non-root folders.";
    public static final String READ_FOLDER_MANIFEST_NOT_FOR_ROOT = "read_folder_manifest is not allowed for a project root folder. Use read_project_manifest instead.";
}
