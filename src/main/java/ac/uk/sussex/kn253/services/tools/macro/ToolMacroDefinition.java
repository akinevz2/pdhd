package ac.uk.sussex.kn253.services.tools.macro;

import java.util.*;

/**
 * Metadata for a single tool macro: canonical name, mandatory description,
 * output signal prefixes, and invocation keyphrases for alias resolution.
 *
 * <ul>
 * <li>{@code description} – human-readable tool purpose, used directly as the
 * LangChain4j {@code ToolSpecification} description.</li>
 * <li>{@code signals} – machine-readable map from logical signal name (e.g.
 * {@code "error"}, {@code "unavailable"}) to the output prefix string that
 * callers test to detect a specific result state without parsing free-form
 * text.</li>
 * <li>{@code invocationKeyphrases} – aliases used for fuzzy tool-name
 * resolution.</li>
 * </ul>
 */
public record ToolMacroDefinition(
        String name,
        String description,
        Map<String, String> signals,
        List<String> invocationKeyphrases) {

    public ToolMacroDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        signals = signals == null ? Map.of() : Map.copyOf(signals);
        invocationKeyphrases = invocationKeyphrases == null ? List.of() : List.copyOf(invocationKeyphrases);
    }
}