package ac.uk.sussex.kn253.menu;

import org.jline.prompt.ListBuilder;

public interface MenuOption {
    String label();

    static void exitOption(final ListBuilder listPrompt) {
        listPrompt
                .newItem("EXIT")
                .text("Exit menu")
                .add();
    }

    String code();
}
