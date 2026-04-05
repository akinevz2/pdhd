package ac.uk.sussex.kn253.resources;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import ac.uk.sussex.kn253.Main;
import ac.uk.sussex.kn253.entities.LLMSettings;
import ac.uk.sussex.kn253.model.ollama.OllamaConfig;
import ac.uk.sussex.kn253.resources.api.model.*;
import ac.uk.sussex.kn253.schema.SchemaKeys;
import ac.uk.sussex.kn253.schema.ToolSupport;
import ac.uk.sussex.kn253.services.ModelConfigService;
import ac.uk.sussex.kn253.services.OllamaManagementService;
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
    private static final String ERROR_REQUEST_BODY_REQUIRED = "Request body is required";
    private static final String ERROR_READ_SETTING_FIELD_PREFIX = "Failed to read setting field: ";
    private static final String ERROR_APPLY_SETTING_PREFIX = "Failed to apply setting: ";
    private static final String ERROR_INVALID_SETTING_PREFIX = "Invalid value for setting '";
    private static final String ERROR_INVALID_SETTING_SUFFIX = "'";
    private static final String ERROR_MISSING_REQUIRED_FIELD_PREFIX = "Missing required field: ";
    private static final String EMPTY_HINT = "";
    private static final Set<String> EDITABLE_EXCLUDED_FIELDS = Set.of(SchemaKeys.ID);
    private static final Set<String> MAPPED_SETTING_FIELDS = Set.of(
            SchemaKeys.FIELD_PROVIDER,
            SchemaKeys.FIELD_BASE_URL,
            SchemaKeys.FIELD_MODEL_NAME);
    private static final Set<String> PROMPT_FIELDS = Set.of(
            SchemaKeys.FIELD_SYSTEM_PROMPT,
            SchemaKeys.FIELD_TOOL_SYSTEM_PROMPT);
    private static final Map<String, String> FIELD_HINTS = Map.of();

    @Inject
    ModelConfigService ollamaConfigService;

    @Inject
    OllamaConfig ollamaConfig;

    @Inject
    OllamaManagementService ollamaManagementService;

    @Inject
    Main main;

    @GET
    @Path("/ollama")
    public LLMSettingsResponse ollamaSettings() {
        return toResponse(ollamaConfigService.load());
    }

    @POST
    @Path("/ollama")
    public LLMSettingsResponse saveLLMSettings(final LLMSettingsRequest request) {
        if (request == null) {
            throw new WebApplicationException(ERROR_REQUEST_BODY_REQUIRED, Response.Status.BAD_REQUEST);
        }

        final LLMSettings settings = ollamaConfigService.load();

        applyDynamicSettings(settings, request.settings());

        if (request.baseUrl() != null) {
            settings.setBaseUrl(normalizeRequired(request.baseUrl(), SchemaKeys.FIELD_BASE_URL));
        }
        if (request.modelName() != null) {
            settings.setModelName(normalizeRequired(request.modelName(), SchemaKeys.FIELD_MODEL_NAME));
        }
        settings.setBaseUrl(normalizeRequired(settings.getBaseUrl(), SchemaKeys.FIELD_BASE_URL));
        settings.setModelName(normalizeRequired(settings.getModelName(), SchemaKeys.FIELD_MODEL_NAME));

        ollamaConfigService.save(settings);
        // chatService.reconfigure(settings);
        return toResponse(settings);
    }

    @GET
    @Path("/ollama/models")
    public Map<String, List<String>> listOllamaModels(@QueryParam("baseUrl") final String baseUrl) {
        final List<String> models = ollamaConfigService.refreshModelCache().stream()
                .map(model -> model.runtimeName())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return Map.of(SchemaKeys.MODELS, models);
    }

    @POST
    @Path("/ollama/models/pull")
    public Map<String, List<String>> pullOllamaModel(final OllamaModelMutationRequest request) {
        final String modelName = normalizeRequired(request != null ? request.modelName() : null,
                SchemaKeys.FIELD_MODEL_NAME);
        ollamaManagementService.pullModel(modelName);
        return Map.of(SchemaKeys.MODELS, ollamaConfigService.refreshModelCache().stream()
                .map(model -> model.runtimeName())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    }

    @POST
    @Path("/ollama/models/delete")
    public Map<String, List<String>> deleteOllamaModel(final OllamaModelMutationRequest request) {
        final String modelName = normalizeRequired(request != null ? request.modelName() : null,
                SchemaKeys.FIELD_MODEL_NAME);
        ollamaManagementService.deleteModel(modelName);
        final LLMSettings settings = ollamaConfigService.load();
        if (modelName.equals(settings.getModelName())) {
            settings.setModelName(ollamaConfig.modelName());
            ollamaConfigService.save(settings);
        }
        return Map.of(SchemaKeys.MODELS, ollamaConfigService.refreshModelCache().stream()
                .map(model -> model.runtimeName())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    }

    @POST
    @Path("/exit")
    public Response exitApplication() {
        // main.exit();
        return Response.accepted(Map.of(SchemaKeys.STATUS, ToolSupport.VALUE_SHUTTING_DOWN)).build();
    }

    private LLMSettingsResponse toResponse(final LLMSettings settings) {
        final Map<String, Object> settingsMap = buildSettingsMap(settings);
        final List<OllamaSettingFieldResponse> settingFields = buildSettingFields(settingsMap);
        return new LLMSettingsResponse(
                settingsMap,
                settingFields,
                settings.getBaseUrl(),
                settings.getModelName(),
                ollamaConfig.timeoutSeconds(),
                ollamaConfig.temperature(),
                ollamaConfig.numPredict(),
                ollamaConfig.numCtx(),
                normalizePrompt(settings.getSystemPrompt()),
                LLMSettings.DEFAULT_SYSTEM_PROMPT,
                normalizeToolPrompt(settings.getToolSystemPrompt()),
                LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
    }

    private Map<String, Object> buildSettingsMap(final LLMSettings settings) {
        final Map<String, Object> values = new LinkedHashMap<>();
        for (final Field field : LLMSettings.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            if (EDITABLE_EXCLUDED_FIELDS.contains(field.getName())) {
                continue;
            }
            if (!MAPPED_SETTING_FIELDS.contains(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            try {
                values.put(field.getName(), field.get(settings));
            } catch (final IllegalAccessException e) {
                throw new WebApplicationException(
                        ERROR_READ_SETTING_FIELD_PREFIX + field.getName(),
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
                    FIELD_HINTS.getOrDefault(key, EMPTY_HINT),
                    minForKey(key),
                    maxForKey(key),
                    stepForKey(key),
                    key.equals(SchemaKeys.FIELD_MODEL_NAME)));
        }
        return fields;
    }

    private void applyDynamicSettings(final LLMSettings settings, final Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        for (final Map.Entry<String, Object> entry : incoming.entrySet()) {
            final String key = entry.getKey();
            if (key == null || key.isBlank() || EDITABLE_EXCLUDED_FIELDS.contains(key)) {
                continue;
            }
            if (!MAPPED_SETTING_FIELDS.contains(key)) {
                continue;
            }

            final Field field;
            try {
                field = LLMSettings.class.getDeclaredField(key);
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
                        ERROR_APPLY_SETTING_PREFIX + key,
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
            if (targetType.isEnum()) {
                final String enumValue = String.valueOf(rawValue).trim().toUpperCase(Locale.ROOT);
                @SuppressWarnings({ "rawtypes", "unchecked" })
                final Object value = Enum.valueOf((Class<? extends Enum>) targetType.asSubclass(Enum.class), enumValue);
                return value;
            }
            return rawValue;
        } catch (final RuntimeException e) {
            throw new WebApplicationException(
                    ERROR_INVALID_SETTING_PREFIX + key + ERROR_INVALID_SETTING_SUFFIX,
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
        return ToolSupport.VALUE_INPUT_TEXT;
    }

    private Double minForKey(final String key) {
        return null;
    }

    private Double maxForKey(final String key) {
        return null;
    }

    private Double stepForKey(final String key) {
        return null;
    }

    private String normalizeRequired(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new WebApplicationException(
                    ERROR_MISSING_REQUIRED_FIELD_PREFIX + fieldName,
                    Response.Status.BAD_REQUEST);
        }
        return value.trim();
    }

    private String normalizePrompt(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return LLMSettings.DEFAULT_SYSTEM_PROMPT;
        }
        return prompt.trim();
    }

    private String normalizeToolPrompt(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT;
        }
        return prompt.trim();
    }

}