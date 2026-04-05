package ac.uk.sussex.kn253.menu;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

import org.jline.prompt.*;
import org.jline.utils.AttributedString;

public record Menu(Prompter prompter, Supplier<List<AttributedString>> headerSupplier, String settingName) {

    public Menu(final Prompter prompter, final String menuName, final String settingName) {
        this(prompter, () -> List.of(new AttributedString(menuName)), settingName);
    }

    public Menu(final Prompter prompter, final List<AttributedString> header, final String settingName) {
        this(prompter, () -> header, settingName);
    }

    public void call(final Map<MenuOption, MenuCallback> callbacks) throws IOException {
        // NOTE: tailcall
        final var option = select(callbacks);
        if (option == null) {
            return;
        }

        dispatch(option, callbacks);
        call(callbacks);
    }

    public MenuOption select(final Map<MenuOption, MenuCallback> callbacks) throws IOException {
        final PromptBuilder builder = prompter.newBuilder();

        final ListBuilder listPrompt = builder.createListPrompt()
                .name("ollama-config-menu")
                .message("Edit " + settingName);
        final var entryList = new ArrayList<>(callbacks.keySet());
        for (final var option : entryList) {
            listPrompt
                    .newItem(option.code())
                    .text(option.label())
                    .add();
        }
        MenuOption.exitOption(listPrompt);
        listPrompt.addPrompt();

        final PromptResult<? extends Prompt> result = prompter
                .prompt(headerSupplier.get(), builder.build())
                .get("ollama-config-menu");
        if (!(result instanceof final ListResult listResult)) {
            return null;
        }
        final var idSelection = listResult.getSelectedId();
        for (final var option : entryList) {
            if (option.code().equals(idSelection)) {
                return option;
            }
        }
        return null;
    }

    public void dispatch(final MenuOption option, final Map<MenuOption, MenuCallback> callbacks) throws IOException {
        final MenuCallback callback = callbacks.get(option);
        if (callback == null) {
            return;
        }
        callback.call();
    }
}
