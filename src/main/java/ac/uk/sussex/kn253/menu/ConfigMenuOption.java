package ac.uk.sussex.kn253.menu;

public enum ConfigMenuOption implements MenuOption {
    UPDATE_PROVIDER("Update provider"),
    UPDATE_BASE_URL("Update Ollama base URL"),
    REFRESH_MODEL_CACHE("Refresh model cache"),
    UPDATE_MODEL("Update model"),
    UPDATE_EMBEDDING_MODEL("Update embedding model"),
    PULL_MODEL("Pull model"),
    DELETE_MODEL("Delete model");

    private final String label;

    ConfigMenuOption(final String labelString) {
        this.label = labelString;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String code() {
        return name();
    }
}
