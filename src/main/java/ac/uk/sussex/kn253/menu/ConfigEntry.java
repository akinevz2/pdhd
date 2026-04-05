package ac.uk.sussex.kn253.menu;

public record ConfigEntry(ConfigMenuOption option, String label) implements MenuOption {
    @Override
    public String code() {
        return option.name();
    }

    @Override
    public String label() {
        return label;
    }
}
