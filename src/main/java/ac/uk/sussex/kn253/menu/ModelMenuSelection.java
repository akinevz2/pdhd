package ac.uk.sussex.kn253.menu;

import ac.uk.sussex.kn253.repository.OllamaModelInfo;

public record ModelMenuSelection(String label, String code) implements MenuOption {
    public ModelMenuSelection(final OllamaModelInfo model) {
        this(model.getName(), model.getModel());
    }
}
