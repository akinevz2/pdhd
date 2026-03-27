package ac.uk.sussex.kn253.api;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ac.uk.sussex.kn253.api.model.OllamaSettingsRequest;
import ac.uk.sussex.kn253.api.model.OllamaSettingsResponse;
import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.services.ChatService;
import ac.uk.sussex.kn253.services.OllamaConfigService;
import io.quarkus.runtime.Quarkus;
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

    @Inject
    OllamaConfigService ollamaConfigService;

    @Inject
    ChatService chatService;

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
        settings.setBaseUrl(normalizeRequired(request.baseUrl(), "baseUrl"));
        settings.setModelName(normalizeRequired(request.modelName(), "modelName"));
        settings.setTimeoutSeconds(
                request.timeoutSeconds() == null ? settings.getTimeoutSeconds() : request.timeoutSeconds());
        settings.setTemperature(request.temperature() == null ? settings.getTemperature() : request.temperature());
        settings.setNumPredict(request.numPredict() == null ? settings.getNumPredict() : request.numPredict());
        settings.setNumCtx(request.numCtx() == null ? settings.getNumCtx() : request.numCtx());
        settings.setSystemPrompt(normalizePrompt(request.systemPrompt()));
        settings.setToolSystemPrompt(normalizeToolPrompt(request.toolSystemPrompt()));

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
        return Map.of("models", fetchModelNames(resolvedBaseUrl));
    }

    @POST
    @Path("/exit")
    public Response exitApplication() {
        Quarkus.asyncExit();
        return Response.accepted(Map.of("status", "shutting-down")).build();
    }

    private OllamaSettingsResponse toResponse(final OllamaSettings settings) {
        return new OllamaSettingsResponse(
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