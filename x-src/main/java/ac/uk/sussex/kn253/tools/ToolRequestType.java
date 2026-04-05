package ac.uk.sussex.kn253.tools;

import java.util.EnumSet;

import org.jspecify.annotations.NonNull;

/**
 * Defines the high-level request context used to filter available tools.
 */
public enum ToolRequestType {
    ALL(EnumSet.allOf(ToolOperationType.class)),
    CHAT(EnumSet.of(
            ToolOperationType.EXPLORE,
            ToolOperationType.READ,
            ToolOperationType.INTROSPECT,
            ToolOperationType.WRITE)),
    FOLDER_SUMMARY(EnumSet.of(
            ToolOperationType.EXPLORE,
            ToolOperationType.READ,
            ToolOperationType.INTROSPECT)),
    READ_ONLY(EnumSet.of(
            ToolOperationType.EXPLORE,
            ToolOperationType.READ,
            ToolOperationType.INTROSPECT));

    private final EnumSet<@NonNull ToolOperationType> allowedOperations;

    ToolRequestType(final EnumSet<@NonNull ToolOperationType> allowedOperations) {
        this.allowedOperations = EnumSet.copyOf(allowedOperations);
    }

    public EnumSet<@NonNull ToolOperationType> allowedOperations() {
        return EnumSet.copyOf(allowedOperations);
    }
}
