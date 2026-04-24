package ac.uk.sussex.kn253.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.repository.LLMSettings;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

@QuarkusTest
class MenuResourceConfigApiTest {

    @Inject
    MenuResource menuResource;

    @BeforeEach
    @Transactional
    void resetSettings() {
        LLMSettings.deleteAll();
    }

    // ── GET /ollama ────────────────────────────────────────────────────────────

    @Test
    void getOllamaConfigurationReturnsSettingsResponseWithExpectedFields() {
        final Object response = menuResource.get("/ollama", null, null);

        assertInstanceOf(MenuResource.OllamaSettingsResponse.class, response);
        final MenuResource.OllamaSettingsResponse settings = (MenuResource.OllamaSettingsResponse) response;
        assertNotNull(settings.settings(), "settings map must not be null");
        assertNotNull(settings.settingFields(), "settingFields must not be null");
        assertFalse(settings.settingFields().isEmpty(), "settingFields must have at least one entry");
    }

    // ── POST /ollama ───────────────────────────────────────────────────────────

    @Test
    @Transactional
    void saveOllamaConfigurationPersistsBaseUrlAndModelName() {
        menuResource.post("/ollama", new MenuResource.MenuSignalRequest(
                Map.of("baseUrl", "http://custom:11434", "modelName", "llama3"),
                null, null, null));

        final MenuResource.OllamaSettingsResponse response = (MenuResource.OllamaSettingsResponse) menuResource
                .get("/ollama", null, null);
        assertEquals("http://custom:11434", response.baseUrl());
        assertEquals("llama3", response.modelName());
    }

    @Test
    @Transactional
    void saveOllamaConfigurationWithNullRequestIsIdempotent() {
        // Should not throw; loads defaults and returns them unchanged.
        final Object response = menuResource.post("/ollama", null);
        assertInstanceOf(MenuResource.OllamaSettingsResponse.class, response);
    }

    // ── POST /ollama/runtime/provider ──────────────────────────────────────────

    @Test
    @Transactional
    void setRuntimeProviderInternalPersistsDockerUrl() {
        menuResource.post("/ollama/runtime/provider",
                new MenuResource.MenuSignalRequest(null, null, null, "INTERNAL"));

        final MenuResource.OllamaSettingsResponse settings = (MenuResource.OllamaSettingsResponse) menuResource
                .get("/ollama", null, null);
        assertEquals("http://host.docker.internal:11434", settings.baseUrl());
    }

    @Test
    @Transactional
    void setRuntimeProviderExternalPersistsCustomBaseUrl() {
        menuResource.post("/ollama/runtime/provider",
                new MenuResource.MenuSignalRequest(null, null, "http://remote:11434", "EXTERNAL"));

        final MenuResource.OllamaSettingsResponse settings = (MenuResource.OllamaSettingsResponse) menuResource
                .get("/ollama", null, null);
        assertEquals("http://remote:11434", settings.baseUrl());
    }

    @Test
    void setRuntimeProviderExternalWithoutBaseUrlThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> menuResource.post("/ollama/runtime/provider",
                new MenuResource.MenuSignalRequest(null, null, null, "EXTERNAL")));
    }

    @Test
    void setRuntimeProviderExternalWithBlankBaseUrlThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> menuResource.post("/ollama/runtime/provider",
                new MenuResource.MenuSignalRequest(null, null, "  ", "EXTERNAL")));
    }

    @Test
    void setRuntimeProviderNullRequestThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> menuResource.post("/ollama/runtime/provider", null));
    }

    @Test
    void setRuntimeProviderUnknownProviderThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> menuResource.post("/ollama/runtime/provider",
                new MenuResource.MenuSignalRequest(null, null, null, "CLOUD")));
    }

    // ── GET /ollama/models ─────────────────────────────────────────────────────

    @Test
    void listOllamaModelsReturnsResponseObjectWhenOllamaUnreachable() {
        final Object response = menuResource.get("/ollama/models", null, "http://unreachable-host:11434");

        assertInstanceOf(MenuResource.OllamaModelsResponse.class, response);
        final MenuResource.OllamaModelsResponse models = (MenuResource.OllamaModelsResponse) response;
        assertNotNull(models.models(), "models list must not be null");
    }

    // ── POST /ollama/models/pull ───────────────────────────────────────────────

    @Test
    void pullModelWithNullModelNameThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> menuResource.post("/ollama/models/pull",
                new MenuResource.MenuSignalRequest(null, null, null, null)));
    }

    @Test
    void pullModelWithBlankModelNameThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> menuResource.post("/ollama/models/pull",
                new MenuResource.MenuSignalRequest(null, "  ", null, null)));
    }

    // ── POST /ollama/models/delete ─────────────────────────────────────────────

    @Test
    void deleteModelWithNullModelNameThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> menuResource.post("/ollama/models/delete",
                new MenuResource.MenuSignalRequest(null, null, null, null)));
    }

    @Test
    void deleteModelWithBlankModelNameThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> menuResource.post("/ollama/models/delete",
                new MenuResource.MenuSignalRequest(null, "  ", null, null)));
    }

    // ── Unknown operations ─────────────────────────────────────────────────────

    @Test
    void unknownGetOperationThrowsNotFoundException() {
        assertThrows(NotFoundException.class, () -> menuResource.get("/ollama/unknown-operation", null, null));
    }

    @Test
    void unknownPostOperationThrowsNotFoundException() {
        assertThrows(NotFoundException.class, () -> menuResource.post("/unknown-operation", null));
    }
}
