package ac.uk.sussex.kn253.tools;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for parsing tool execution arguments.
 *
 * <p>
 * Tool implementations share identical argument parsing and coercion logic.
 * This class centralises that logic to eliminate duplication.
 *
 * <p>
 * All methods are static; this class is not meant to be instantiated.
 */
public final class ToolArguments {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private ToolArguments() {
        // utility class – not instantiable
    }

    /**
     * Parses a JSON string into a {@code Map<String, Object>}.
     *
     * @param json the JSON string to parse; may be {@code null} or blank.
     * @return a non-null map; empty if {@code json} is blank or unparseable.
     */
    public static Map<String, Object> parse(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (final IOException e) {
            return Map.of();
        }
    }

    /**
     * Returns the value for {@code key} as a string, falling back to
     * {@code defaultValue} if the key is absent or its value is {@code null}.
     *
     * @param args         the argument map.
     * @param key          the key to look up.
     * @param defaultValue the value to return when the key is missing.
     * @return a non-null string representation of the value.
     */
    public static String getString(
            final Map<String, Object> args,
            final String key,
            final String defaultValue) {
        final Object val = valueOf(args, key);
        return val == null ? defaultValue : String.valueOf(val);
    }

    /**
     * Returns the value for {@code key} as a required non-blank string.
     *
     * @param args the argument map.
     * @param key  the key to look up.
     * @return the non-blank string value.
     * @throws IllegalArgumentException if the key is absent or the value is blank.
     */
    public static String require(final Map<String, Object> args, final String key) {
        final String val = getString(args, key, "");
        if (val.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return val;
    }

    /**
     * Returns the value for {@code key} as an integer, falling back to
     * {@code defaultValue} if absent. Returns {@code -1} if the value exists but
     * cannot be parsed as a number.
     *
     * @param args         the argument map.
     * @param key          the key to look up.
     * @param defaultValue the value to return when the key is missing.
     * @return the integer value, or {@code -1} on parse failure.
     */
    public static int getInt(
            final Map<String, Object> args,
            final String key,
            final int defaultValue) {
        final Object value = valueOf(args, key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof final Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns the value for {@code key} as a boolean, falling back to
     * {@code defaultValue} when the key is absent.
     *
     * @param args         the argument map.
     * @param key          the key to look up.
     * @param defaultValue the value to return when the key is missing.
     * @return the boolean value.
     */
    public static boolean getBoolean(
            final Map<String, Object> args,
            final String key,
            final boolean defaultValue) {
        final Object value = valueOf(args, key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof final Boolean boolValue) {
            return boolValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object valueOf(final Map<String, Object> args, final String key) {
        return args == null ? null : args.get(key);
    }
}
