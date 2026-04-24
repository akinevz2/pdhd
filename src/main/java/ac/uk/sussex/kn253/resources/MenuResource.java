package ac.uk.sussex.kn253.resources;

import java.util.*;
import java.util.logging.Logger;

import org.jboss.resteasy.reactive.RestStreamElementType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.ollama.OllamaConfig;
import ac.uk.sussex.kn253.ollama.OllamaPullStatus;
import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
import ac.uk.sussex.kn253.services.ModelConfigService;
import ac.uk.sussex.kn253.services.OllamaManagementService;
import io.quarkus.runtime.Quarkus;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints that back the frontend's menu actions.
 */
@jakarta.ws.rs.Path("/api/menu{operation: (/.*)?}")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class MenuResource {

    private static final Logger LOG = Logger.getLogger(MenuResource.class.getName());

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
            String modelName,
            String baseUrl) {
    }

    public record DeleteModelRequest(
            String modelName,
            String baseUrl) {
    }

    public record OllamaRuntimeStatusResponse(
            String runtimeEndpoint,
            String runtimeProvider,
            boolean healthy) {
    }

    public record SetRuntimeProviderRequest(
            String provider,
            String baseUrl) {
    }

    public record MenuSignalRequest(
            Map<String, Object> settings,
            String modelName,
            String baseUrl,
            String provider) {
    }

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    OllamaManagementService ollamaManagementService;

    @Inject
    OllamaConfig ollamaConfig;

    @Inject
    ObjectMapper objectMapper;

    private final String DOCKER_INTERNAL_URL = "http://host.docker.internal:11434";

    private OllamaRuntimeStatusResponse getOllamaRuntimeStatus() {
        final String runtime = resolveRuntimeEndpoint();
        final boolean healthy = ollamaManagementService.isHealthy(runtime);
        return new OllamaRuntimeStatusResponse(
                runtime,
                resolveRuntimeProvider(runtime),
                healthy);
    }

    private OllamaRuntimeStatusResponse setOllamaRuntimeProvider(final SetRuntimeProviderRequest request) {
        if (request == null || request.provider() == null || request.provider().isBlank()) {
            throw new BadRequestException("Provider is required");
        }

        final LLMSettings settings = modelConfigService.load();
        final String provider = request.provider().trim().toUpperCase(Locale.ROOT);
        switch (provider) {
            case "INTERNAL":
                settings.setBaseUrl(DOCKER_INTERNAL_URL);
                break;
            case "EXTERNAL": {
                final String externalBaseUrl = request.baseUrl() == null ? null : request.baseUrl().trim();
                if (externalBaseUrl == null || externalBaseUrl.isBlank()) {
                    throw new BadRequestException("Base URL is required for external provider");
                }
                settings.setBaseUrl(externalBaseUrl);
                break;
            }
            default:
                throw new BadRequestException("Provider is required");
        }

        modelConfigService.save(settings);

        return getOllamaRuntimeStatus();
    }

    @Blocking
    private Multi<String> pullModelStream(
            final String modelName,
            final String baseUrl) {
        if (modelName == null || modelName.isBlank()) {
            throw new BadRequestException("Model name is required");
        }
        return Multi.createFrom().<String>emitter(em -> {
            try {
                ollamaManagementService.pullModelStreaming(normalizeBaseUrl(baseUrl), modelName, status -> {
                    try {
                        em.emit(objectMapper.writeValueAsString(status));
                    } catch (final JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (final RuntimeException e) {
                try {
                    final OllamaPullStatus failedStatus = new OllamaPullStatus(
                            "error: " + e.getMessage(),
                            null,
                            0L,
                            0L);
                    em.emit(objectMapper.writeValueAsString(failedStatus));
                } catch (final Exception ignored) {
                    // Best effort: if status serialization fails, close stream.
                }
            } finally {
                em.complete();
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private OllamaSettingsResponse getOllamaConfiguration() {
        final LLMSettings settings = modelConfigService.load();
        return buildOllamaSettingsResponse(settings);
    }

    private OllamaSettingsResponse saveOllamaConfiguration(final SaveSettingsRequest request) {
        final LLMSettings settings = modelConfigService.load();

        if (request != null && request.settings != null) {
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

    private OllamaModelsResponse listOllamaModels(final String baseUrl) {
        try {
            final String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
            if (normalizedBaseUrl != null) {
                return new OllamaModelsResponse(listModelNames(normalizedBaseUrl));
            }
            return new OllamaModelsResponse(listModelNames(null));
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to list Ollama models: " + e.getMessage());
            return new OllamaModelsResponse(Collections.emptyList());
        }
    }

    private OllamaModelsResponse pullOllamaModel(final PullModelRequest request) {
        if (request == null || request.modelName == null || request.modelName.isBlank()) {
            throw new BadRequestException("Model name is required");
        }

        try {
            final String normalizedBaseUrl = normalizeBaseUrl(request.baseUrl());
            ollamaManagementService.pullModel(normalizedBaseUrl, request.modelName);
            return new OllamaModelsResponse(listModelNames(normalizedBaseUrl));
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to pull model: " + e.getMessage());
            throw new InternalServerErrorException("Failed to pull model: " + e.getMessage());
        }
    }

    private OllamaModelsResponse deleteOllamaModel(final DeleteModelRequest request) {
        if (request == null || request.modelName == null || request.modelName.isBlank()) {
            throw new BadRequestException("Model name is required");
        }

        try {
            final String normalizedBaseUrl = normalizeBaseUrl(request.baseUrl());
            ollamaManagementService.deleteModel(normalizedBaseUrl, request.modelName);
            return new OllamaModelsResponse(listModelNames(normalizedBaseUrl));
        } catch (final Exception e) {
            LOG.warning(() -> "Failed to delete model: " + e.getMessage());
            throw new InternalServerErrorException("Failed to delete model: " + e.getMessage());
        }
    }

    private Response exitApplication() {
        Quarkus.asyncExit();
        return Response.accepted(Map.of("status", "shutting_down")).build();
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.SERVER_SENT_EVENTS })
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Object get(
            @PathParam("operation") final String operation,
            @QueryParam("modelName") final String modelName,
            @QueryParam("baseUrl") final String baseUrl) {
        final String normalized = normalizeOperation(operation);
        return switch (normalized) {
            case "/ollama/status" -> getOllamaRuntimeStatus();
            case "/ollama/models/pull/stream" -> pullModelStream(modelName, baseUrl);
            case "/ollama" -> getOllamaConfiguration();
            case "/ollama/models" -> listOllamaModels(baseUrl);
            default -> throw new NotFoundException("Unsupported menu operation");
        };
    }

    @POST
    public Object post(
            @PathParam("operation") final String operation,
            final MenuSignalRequest request) {
        final String normalized = normalizeOperation(operation);
        return switch (normalized) {
            case "/ollama/runtime/provider" -> setOllamaRuntimeProvider(new SetRuntimeProviderRequest(
                    request == null ? null : request.provider(),
                    request == null ? null : request.baseUrl()));
            case "/ollama" -> saveOllamaConfiguration(new SaveSettingsRequest(
                    request == null ? null : request.settings()));
            case "/ollama/models/pull" -> pullOllamaModel(new PullModelRequest(
                    request == null ? null : request.modelName(),
                    request == null ? null : request.baseUrl()));
            case "/ollama/models/delete" -> deleteOllamaModel(new DeleteModelRequest(
                    request == null ? null : request.modelName(),
                    request == null ? null : request.baseUrl()));
            case "/exit" -> exitApplication();
            default -> throw new NotFoundException("Unsupported menu operation");
        };
    }

    private String normalizeOperation(final String operation) {
        if (operation == null || operation.isBlank()) {
            return "";
        }
        final String trimmed = operation.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
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
                ollamaConfig.timeoutSeconds(),
                0.7,
                256,
                2048,
                settings.getSystemPrompt(),
                LLMSettings.DEFAULT_SYSTEM_PROMPT,
                settings.getToolSystemPrompt(),
                LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT);
    }

    private String resolveRuntimeEndpoint() {
        try {
            final LLMSettings settings = modelConfigService.load();
            final String persistedBaseUrl = settings != null ? settings.getBaseUrl() : null;
            if (persistedBaseUrl != null && !persistedBaseUrl.isBlank()) {
                final String trimmed = persistedBaseUrl.trim();
                // Use persisted value only if it's reachable; otherwise fall through to
                // config/default
                if (ollamaManagementService.isHealthy(trimmed)) {
                    return trimmed;
                }
                LOG.fine(() -> "Persisted Ollama endpoint unreachable: " + trimmed + "; falling back to config");
            }
        } catch (final Exception e) {
            LOG.fine(() -> "Skipping persisted runtime endpoint lookup: " + e.getMessage());
        }

        return ollamaConfig.baseUrl()
                .map(String::trim)
                .filter(baseUrl -> !baseUrl.isBlank())
                .orElse(DOCKER_INTERNAL_URL);
    }

    private String resolveRuntimeProvider(final String runtimeEndpoint) {
        if (runtimeEndpoint == null || runtimeEndpoint.isBlank()) {
            return "EXTERNAL";
        }
        return DOCKER_INTERNAL_URL.equals(runtimeEndpoint) ? "INTERNAL" : "EXTERNAL";
    }

    private String normalizeBaseUrl(final String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        final String trimmed = baseUrl.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private List<String> listModelNames(final String baseUrl) {
        final List<OllamaModelInfo> models = baseUrl == null
                ? modelConfigService.refreshModelCache()
                : ollamaManagementService.listModels(baseUrl);
        return models.stream()
                .map(OllamaModelInfo::getName)
                .toList();
    }
}
