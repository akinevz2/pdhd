package ac.uk.sussex.kn253.api;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ac.uk.sussex.kn253.Main;
import ac.uk.sussex.kn253.api.model.*;
import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.schema.SchemaKeys;
import ac.uk.sussex.kn253.schema.ToolSupport;
import ac.uk.sussex.kn253.services.ChatService;
import ac.uk.sussex.kn253.services.OllamaConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints that back the frontend's top-level menu actions.
 */
@Path("/api/menu")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MenuApiResource {
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Set<String> EDITABLE_EXCLUDED_FIELDS = Set.of("id");
    private static final Set<String> PROMPT_FIELDS = Set.of(
            SchemaKeys.FIELD_SYSTEM_PROMPT,
            SchemaKeys.FIELD_TOOL_SYSTEM_PROMPT);
    private static final Map<String, String> FIELD_HINTS = Map.of(
            SchemaKeys.FIELD_NUM_PREDICT, "-1 = model default",
            SchemaKeys.FIELD_NUM_CTX, "0 = model default",
            SchemaKeys.FIELD_EMBEDDING_BASE_URL, "If blank, falls back to Base URL.",
            SchemaKeys.FIELD_EMBEDDING_MODEL, "Embedding model used for semantic search.");

    @Inject
    OllamaConfigService ollamaConfigService;

    @Inject
    ChatService chatService;

    @Inject
    Main main;

    @GET
    @Path("/ollama")
    public OllamaSettingsResponse ollamaSettings() {
        return toResponse(ollamaConfigService.load());
    }

    @POST
    @Path("/ollama")
    public OllamaSettingsResponse saveOllamaSettings(final OllamaSettingsRequest request) {
        if (request == null) {
            throw new WebApplicationException("Request body is required", Response.Status.BAD_REQUEST);
        }

        final OllamaSettings settings = ollamaConfigService.load();

        applyDynamicSettings(settings, request.settings());

        if (request.baseUrl() != null) {
            settings.setBaseUrl(normalizeRequired(request.baseUrl(), SchemaKeys.FIELD_BASE_URL));
        }
        if (request.modelName() != null) {
            settings.setModelName(normalizeRequired(request.modelName(), SchemaKeys.FIELD_MODEL_NAME));
        }
        if (request.timeoutSeconds() != null) {
            settings.setTimeoutSeconds(request.timeoutSeconds());
        }
        if (request.temperature() != null) {
            settings.setTemperature(request.temperature());
        }
        if (request.numPredict() != null) {
            settings.setNumPredict(request.numPredict());
        }
        if (request.numCtx() != null) {
            settings.setNumCtx(request.numCtx());
        }
        if (request.systemPrompt() != null) {
            settings.setSystemPrompt(normalizePrompt(request.systemPrompt()));
        }
        if (request.toolSystemPrompt() != null) {
            settings.setToolSystemPrompt(normalizeToolPrompt(request.toolSystemPrompt()));
        }

        settings.setBaseUrl(normalizeRequired(settings.getBaseUrl(), SchemaKeys.FIELD_BASE_URL));
        settings.setModelName(normalizeRequired(settings.getModelName(), SchemaKeys.FIELD_MODEL_NAME));
        settings.setSystemPrompt(normalizePrompt(settings.getSystemPrompt()));
        settings.setToolSystemPrompt(normalizeToolPrompt(settings.getToolSystemPrompt()));

        ollamaConfigService.save(settings);
        chatService.reconfigure(settings);
        return toResponse(settings);
    }

    @GET
    @Path("/ollama/models")
    public Map<String, List<String>> listOllamaModels(@QueryParam("baseUrl") final String baseUrl) {
        final String resolvedBaseUrl = baseUrl == null || baseUrl.isBlank()
                ? ollamaConfigService.load().getBaseUrl()
                : baseUrl.trim();
        return Map.of(SchemaKeys.MODELS, fetchModelNames(resolvedBaseUrl));
    }

    @POST
    @Path("/exit")
    public Response exitApplication() {
        main.exit();
        return Response.accepted(Map.of(SchemaKeys.STATUS, ToolSupport.VALUE_SHUTTING_DOWN)).build();
    }

    private OllamaSettingsResponse toResponse(final OllamaSettings settings) {
        final Map<String, Object> settingsMap = buildSettingsMap(settings);
        final List<OllamaSettingFieldResponse> settingFields = buildSettingFields(settingsMap);
        return new OllamaSettingsResponse(
                settingsMap,
                settingFields,
                settings.getBaseUrl(),
                settings.getModelName(),
                settings.getTimeoutSeconds(),
                settings.getTemperature(),
                settings.getNumPredict(),
                settings.getNumCtx(),
                normalizePrompt(settings.getSystemPrompt()),
                OllamaSettings.DEFAULT_SYSTEM_PROMPT,
                normalizeToolPrompt(settings.getToolSystemPrompt()),
                OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
    }

    private Map<String, Object> buildSettingsMap(final OllamaSettings settings) {
        final Map<String, Object> values = new LinkedHashMap<>();
        for (final Field field : OllamaSettings.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            if (EDITABLE_EXCLUDED_FIELDS.contains(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            try {
                values.put(field.getName(), field.get(settings));
            } catch (final IllegalAccessException e) {
                throw new WebApplicationException(
                        "Failed to read setting field: " + field.getName(),
                        Response.Status.INTERNAL_SERVER_ERROR);
            }
        }
        return values;
    }

    private List<OllamaSettingFieldResponse> buildSettingFields(final Map<String, Object> settingsMap) {
        final List<OllamaSettingFieldResponse> fields = new ArrayList<>();
        for (final String key : settingsMap.keySet()) {
            if (PROMPT_FIELDS.contains(key)) {
                continue;
            }
            fields.add(new OllamaSettingFieldResponse(
                    key,
                    humanizeKey(key),
                    inputTypeForKey(key),
                    FIELD_HINTS.getOrDefault(key, ""),
                    minForKey(key),
                    maxForKey(key),
                    stepForKey(key),
                    key.equals(SchemaKeys.FIELD_MODEL_NAME) || key.equals(SchemaKeys.FIELD_EMBEDDING_MODEL)));
        }
        return fields;
    }

    private void applyDynamicSettings(final OllamaSettings settings, final Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        for (final Map.Entry<String, Object> entry : incoming.entrySet()) {
            final String key = entry.getKey();
            if (key == null || key.isBlank() || EDITABLE_EXCLUDED_FIELDS.contains(key)) {
                continue;
            }

            final Field field;
            try {
                field = OllamaSettings.class.getDeclaredField(key);
            } catch (final NoSuchFieldException e) {
                continue;
            }

            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }

            final Object coerced = coerceValue(field.getType(), entry.getValue(), key);
            if (coerced == null && field.getType().isPrimitive()) {
                continue;
            }

            field.setAccessible(true);
            try {
                field.set(settings, coerced);
            } catch (final IllegalAccessException e) {
                throw new WebApplicationException(
                        "Failed to apply setting: " + key,
                        Response.Status.BAD_REQUEST);
            }
        }
    }

    private Object coerceValue(final Class<?> targetType, final Object rawValue, final String key) {
        if (rawValue == null) {
            return null;
        }

        try {
            if (targetType.equals(String.class)) {
                return String.valueOf(rawValue).trim();
            }
            if (targetType.equals(int.class) || targetType.equals(Integer.class)) {
                if (rawValue instanceof final Number number) {
                    return number.intValue();
                }
                return Integer.parseInt(String.valueOf(rawValue).trim());
            }
            if (targetType.equals(double.class) || targetType.equals(Double.class)) {
                if (rawValue instanceof final Number number) {
                    return number.doubleValue();
                }
                return Double.parseDouble(String.valueOf(rawValue).trim());
            }
            if (targetType.equals(boolean.class) || targetType.equals(Boolean.class)) {
                if (rawValue instanceof final Boolean bool) {
                    return bool;
                }
                if (rawValue instanceof final Number number) {
                    return number.intValue() != 0;
                }
                return Boolean.parseBoolean(String.valueOf(rawValue).trim());
            }
            return rawValue;
        } catch (final RuntimeException e) {
            throw new WebApplicationException(
                    "Invalid value for setting '" + key + "'",
                    Response.Status.BAD_REQUEST);
        }
    }

    private String humanizeKey(final String key) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            final char ch = key.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(ch));
                continue;
            }
            if (Character.isUpperCase(ch)) {
                sb.append(' ').append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String inputTypeForKey(final String key) {
        if (key.equals(SchemaKeys.FIELD_TEMPERATURE) || key.endsWith("Seconds") || key.startsWith("num")
                || key.endsWith("Dimension") || key.endsWith("Results")) {
            return ToolSupport.VALUE_INPUT_NUMBER;
        }
        if (key.startsWith(SchemaKeys.FIELD_EMBEDDING_ENABLED) || key.equals(SchemaKeys.FIELD_EMBEDDING_ENABLED)) {
            return ToolSupport.VALUE_INPUT_BOOLEAN;
        }
        return ToolSupport.VALUE_INPUT_TEXT;
    }

    private Double minForKey(final String key) {
        return switch (key) {
            case SchemaKeys.FIELD_TIMEOUT_SECONDS -> 1.0;
            case SchemaKeys.FIELD_TEMPERATURE -> 0.0;
            case SchemaKeys.FIELD_NUM_CTX -> 0.0;
            case SchemaKeys.FIELD_EMBEDDING_DIMENSION -> 1.0;
            case SchemaKeys.FIELD_EMBEDDING_MAX_RESULTS -> 1.0;
            default -> null;
        };
    }

    private Double maxForKey(final String key) {
        return switch (key) {
            case SchemaKeys.FIELD_TEMPERATURE -> 2.0;
            case SchemaKeys.FIELD_EMBEDDING_MAX_RESULTS -> 50.0;
            default -> null;
        };
    }

    private Double stepForKey(final String key) {
        return switch (key) {
            case SchemaKeys.FIELD_TEMPERATURE -> 0.05;
            default -> 1.0;
        };
    }

    private String normalizeRequired(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new WebApplicationException("Missing required field: " + fieldName, Response.Status.BAD_REQUEST);
        }
        return value.trim();
    }

    private String normalizePrompt(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return OllamaSettings.DEFAULT_SYSTEM_PROMPT;
        }
        return prompt.trim();
    }

    private String normalizeToolPrompt(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return OllamaSettings.DEFAULT_TOOL_SYSTEM_PROMPT;
        }
        return prompt.trim();
    }

    private List<String> fetchModelNames(final String baseUrl) {
        try {
            final String url = baseUrl.replaceAll("/+$", "") + "/api/tags";
            final HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }

            final Matcher matcher = MODEL_NAME_PATTERN.matcher(response.body());
            final List<String> names = new ArrayList<>();
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            return names;
        } catch (final Exception e) {
            return List.of();
        }
    }
}