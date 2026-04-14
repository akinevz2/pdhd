package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
import jakarta.persistence.EntityManager;

class ModelConfigServiceTest {

    @Test
    void refreshModelCacheReturnsCachedWhenEndpointUnhealthy() throws Exception {
        final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        final LLMSettings settings = settingsWithCache(mapper, "http://offline:11434", List.of(model("cached-model")));

        final StubOllamaManagementService ollama = new StubOllamaManagementService();
        ollama.healthy = false;
        ollama.liveModels = List.of(model("live-model"));

        final TestableModelConfigService service = new TestableModelConfigService(settings, mapper, ollama);

        final List<OllamaModelInfo> models = service.refreshModelCache();

        assertEquals(1, models.size());
        assertEquals("cached-model", models.get(0).getName());
        assertTrue(settings.getOllamaModelsJson().contains("cached-model"));
    }

    @Test
    void refreshModelCacheUsesLiveModelsAndUpdatesCacheWhenHealthy() throws Exception {
        final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        final LLMSettings settings = settingsWithCache(mapper, "http://live:11434", List.of(model("cached-model")));

        final StubOllamaManagementService ollama = new StubOllamaManagementService();
        ollama.healthy = true;
        ollama.liveModels = List.of(model("live-model-a"), model("live-model-b"));

        final TestableModelConfigService service = new TestableModelConfigService(settings, mapper, ollama);

        final List<OllamaModelInfo> models = service.refreshModelCache();

        assertEquals(2, models.size());
        assertEquals("live-model-a", models.get(0).getName());
        assertNotNull(settings.getOllamaModelsJson());
        assertTrue(settings.getOllamaModelsJson().contains("live-model-b"));
    }

    @Test
    void refreshModelCacheFallsBackToCachedWhenLiveFetchFails() throws Exception {
        final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        final LLMSettings settings = settingsWithCache(mapper, "http://live:11434", List.of(model("cached-model")));

        final StubOllamaManagementService ollama = new StubOllamaManagementService();
        ollama.healthy = true;
        ollama.throwOnList = true;

        final TestableModelConfigService service = new TestableModelConfigService(settings, mapper, ollama);

        final List<OllamaModelInfo> models = service.refreshModelCache();

        assertEquals(1, models.size());
        assertEquals("cached-model", models.get(0).getName());
        assertTrue(settings.getOllamaModelsJson().contains("cached-model"));
    }

    private static LLMSettings settingsWithCache(
            final ObjectMapper mapper,
            final String baseUrl,
            final List<OllamaModelInfo> models) throws Exception {
        final LLMSettings settings = new LLMSettings();
        settings.setBaseUrl(baseUrl);
        settings.setModelName("gemma4");
        settings.setEmbeddingModelName("qwen3-embedding");
        settings.setOllamaModelsJson(mapper.writeValueAsString(models));
        return settings;
    }

    private static OllamaModelInfo model(final String name) {
        final OllamaModelInfo info = new OllamaModelInfo();
        info.setName(name);
        info.setModel(name);
        return info;
    }

    private static final class StubOllamaManagementService extends OllamaManagementService {
        boolean healthy;
        boolean throwOnList;
        List<OllamaModelInfo> liveModels = List.of();

        @Override
        public boolean isHealthy(final String baseUrl) {
            return healthy;
        }

        @Override
        public List<OllamaModelInfo> listModels(final String baseUrl) {
            if (throwOnList) {
                throw new IllegalStateException("boom");
            }
            return liveModels;
        }
    }

    private static final class TestableModelConfigService extends ModelConfigService {
        private final LLMSettings settings;

        private TestableModelConfigService(
                final LLMSettings settings,
                final ObjectMapper objectMapper,
                final OllamaManagementService ollamaService) {
            this.settings = settings;
            this.objectMapper = objectMapper;
            this.ollamaManagementService = ollamaService;
            this.em = passthroughEntityManager();
        }

        @Override
        public LLMSettings load() {
            return settings;
        }
    }

    private static EntityManager passthroughEntityManager() {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[] { EntityManager.class },
                (proxy, method, args) -> {
                    if ("merge".equals(method.getName()) && args != null && args.length == 1) {
                        return args[0];
                    }
                    if ("isOpen".equals(method.getName())) {
                        return true;
                    }
                    return null;
                });
    }
}
