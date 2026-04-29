package ac.uk.sussex.kn253.tools;

import java.util.Optional;

/**
 * Marker and capability interface for objects that describe and optionally
 * execute a single tool in the explicit dispatch path.
 *
 * <p>
 * Every implementing class must:
 * <ul>
 * <li>Contain no reflections.
 * <li>Contain no switch statements.
 * <li>Contain no ternary expressions.
 * <li>Contain no boxed types.
 * </ul>
 *
 * <p>
 * The two optional methods ({@link #bashUtil()} and {@link #call(String...)})
 * default to {@link Optional#empty()} so that tool implementations that do not
 * need them incur no boilerplate. Only {@link #schema()} is mandatory.
 *
 * <p>
 * All {@code ToolContainer} beans are collected at startup by
 * {@link ToolRegistry}, which builds {@link ToolDefinition} instances once and
 * exposes them for dispatch and schema validation.
 */
public interface ToolContainer {

    /**
     * Returns the name of a bash utility associated with this tool, if any.
     * Implementations that wrap a known shell command may return it here to allow
     * callers to inspect or log the underlying executable.
     *
     * @return an {@link Optional} containing the bash utility name, or
     *         {@link Optional#empty()} if this tool does not have one
     */
    default Optional<String> bashUtil() {
        return Optional.empty();
    }

    /**
     * Invokes the tool as a subprocess with the supplied arguments, if this tool
     * supports direct process execution. Returns an {@link Optional} wrapping a
     * configured {@link ProcessBuilder} ready to start; the caller is responsible
     * for starting and managing the process lifecycle.
     *
     * <p>
     * Implementations that do not support subprocess execution must return
     * {@link Optional#empty()}.
     *
     * @param args the arguments to pass to the process; may be empty
     * @return an {@link Optional} containing a configured {@link ProcessBuilder},
     *         or {@link Optional#empty()} if subprocess execution is not supported
     */
    default Optional<ProcessBuilder> call(final String... args) {
        return Optional.empty();
    }

    /**
     * Returns the {@link ToolDefinition} that describes this tool's name,
     * human-readable description, and JSON schema.
     *
     * <p>
     * This method is called once at startup by {@link ToolRegistry}. The returned
     * instance must be stable and must not change after the application has
     * started.
     *
     * @return the {@link ToolDefinition} for this tool; never {@code null}
     */
    ToolDefinition schema();
}
