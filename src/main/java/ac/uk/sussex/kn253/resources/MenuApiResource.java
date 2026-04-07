package ac.uk.sussex.kn253.resources;

import java.util.*;
import java.util.logging.Logger;

import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
import ac.uk.sussex.kn253.services.ModelConfigService;
import ac.uk.sussex.kn253.services.OllamaManagementService;
import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints that back the frontend's menu actions.
 */
@Path("/api/menu")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MenuApiResource {

    private static final Logger LOG = Logger.getLogger(MenuApiResource.class.getName());

    public record OllamaSettingField(
            String key,
            String label,
            String inputType,
            String hint,
            Double min,
            Double max,
            Double step,
            boolean modelField) {
    }

    public record OllamaSettingsResponse(
            Map<String, Object> settings,
            List<OllamaSettingField> settingFields,
            String baseUrl,
            String modelName,
            int timeoutSeconds,
            double temperature,
            int numPredict,
            int numCtx,
            String systemPrompt,
            String defaultSystemPrompt,
            String toolSystemPrompt,
            String defaultToolSystemPrompt) {
    }

    public record OllamaModelsResponse(
            List<String> models) {
    }

    public record SaveSettingsRequest(
            Map<String, Object> settings) {
    }

    public record PullModelRequest(
            String modelName) {
    }

    public record DeleteModelRequest(
            String modelName) {
    }

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    OllamaManagementService ollamaManagementService;

    @GET
    @Path("/ollama")
    @Transactional
    public OllamaSettingsResponse getOllamaConfiguration() {
        final LLMSettings settings = modelConfigService.load();
        return buildOllamaSettingsResponse(settings);
    }

    @POST
    @Path("/ollama")
    @Transactional
    public OllamaSettingsResponse saveOllamaConfiguration(final SaveSettingsRequest request) {
        final LLMSettings settings = modelConfigService.load();

        if (request.settings != null) {
            final Object baseUrl = request.settings.get("baseUrl");
            if (baseUrl != null) {
                settings.setBaseUrl(String.valueOf(baseUrl));
            }
            final Object modelName = request.settings.get("modelName");
            if (modelName != null) {
                settings.setModelName(String.valueOf(modelName));
            }
            final Object embeddingModelName = request.settings.get("embeddingModelName");
            if (embeddingModelName != null) {
                settings.setEmbeddingModelName(String.valueOf(embeddingModelName));
            }
        }

        modelConfigService.save(settings);
        return buildOllamaSettingsResponse(modelConfigService.load());
    }

    @GET
    @Path("/ollama/models")
    @Transactional
    public OllamaModelsResponse listOllamaModels() {
        try {
            final List<OllamaModelInfo> models = modelConfigService.refreshModelCache();
            final List<String> modelNames = models.stream()
                    .map(OllamaModelInfo::getName)
                    .toList();
            return new OllamaModelsResponse(modelNames);
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to list Ollama models: " + e.getMessage());
            return new OllamaModelsResponse(Collections.emptyList());
        }
    }

    @POST
    @Path("/ollama/models/pull")
    @Transactional
    public OllamaModelsResponse pullOllamaModel(final PullModelRequest request) {
        if (request == null || request.modelName == null || request.modelName.isBlank()) {
            throw new WebApplicationException("Model name is required", Response.Status.BAD_REQUEST);
        }

        try {
            ollamaManagementService.pullModel(request.modelName);
            final List<OllamaModelInfo> models = modelConfigService.refreshModelCache();
            final List<String> modelNames = models.stream()
                    .map(OllamaModelInfo::getName)
                    .toList();
            return new OllamaModelsResponse(modelNames);
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to pull model: " + e.getMessage());
            throw new WebApplicationException("Failed to pull model: " + e.getMessage(),
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/ollama/models/delete")
    @Transactional
    public OllamaModelsResponse deleteOllamaModel(final DeleteModelRequest request) {
        if (request == null || request.modelName == null || request.modelName.isBlank()) {
            throw new WebApplicationException("Model name is required", Response.Status.BAD_REQUEST);
        }

        try {
            ollamaManagementService.deleteModel(request.modelName);
            final List<OllamaModelInfo> models = modelConfigService.refreshModelCache();
            final List<String> modelNames = models.stream()
                    .map(OllamaModelInfo::getName)
                    .toList();
            return new OllamaModelsResponse(modelNames);
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to delete model: " + e.getMessage());
            throw new WebApplicationException("Failed to delete model: " + e.getMessage(),
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/exit")
    public Response exitApplication() {
        Quarkus.asyncExit();
        return Response.accepted(Map.of("status", "shutting_down")).build();
    }

    // -------

    private OllamaSettingsResponse buildOllamaSettingsResponse(final LLMSettings settings) {
        final Map<String, Object> settingsMap = new LinkedHashMap<>();
        settingsMap.put("baseUrl", settings.getBaseUrl());
        settingsMap.put("modelName", settings.getModelName());
        settingsMap.put("embeddingModelName", settings.getEmbeddingModelName());

        final List<OllamaSettingField> fields = List.of(
                new OllamaSettingField("baseUrl", "Ollama Base URL", "text", "The HTTP URL to your Ollama instance",
                        null, null, null, false),
                new OllamaSettingField("modelName", "Chat Model", "text",
                        "Model used for chat interactions", null, null, null, true),
                new OllamaSettingField("embeddingModelName", "Embedding Model", "text",
                        "Model used for embeddings", null, null, null, false));

        return new OllamaSettingsResponse(
                settingsMap,
                fields,
                settings.getBaseUrl(),
                settings.getModelName(),
                30,
                0.7,
                256,
                2048,
                settings.getSystemPrompt(),
                LLMSettings.DEFAULT_SYSTEM_PROMPT,
                settings.getToolSystemPrompt(),
                LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
    }
}
