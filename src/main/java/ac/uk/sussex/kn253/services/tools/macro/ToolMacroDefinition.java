package ac.uk.sussex.kn253.services.tools.macro;

import java.util.List;
import java.util.Objects;

public record ToolMacroDefinition(String name, List<String> invocationKeyphrases) {

    public ToolMacroDefinition {
        Objects.requireNonNull(name, "name");
        invocationKeyphrases = invocationKeyphrases == null ? List.of() : List.copyOf(invocationKeyphrases);
    }
}