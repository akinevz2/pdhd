package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.ollama.*;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;

class OllamaManagementServiceTest {

    @Test
    void isHealthyThrottlesRepeatedFailureWarningsForSameEndpoint() {
        final TestableOllamaManagementService service = newService("http://configured-host:22434",
                clientReturningModels(List.of()));
        service.probeFailure = new RuntimeException("boom");

        final Logger logger = Logger.getLogger(OllamaManagementService.class.getName());
        final AtomicInteger warningCount = new AtomicInteger();
        final Handler handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()
                        && record.getMessage() != null
                        && record.getMessage().contains("Ollama health check failed for")) {
                    warningCount.incrementAndGet();
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        logger.addHandler(handler);
        try {
            assertTrue(!service.isHealthy(" "));
            assertTrue(!service.isHealthy(" "));
        } finally {
            logger.removeHandler(handler);
        }

        assertEquals(1, warningCount.get());
    }

    @Test
    void isHealthyFallsBackToConfiguredBaseUrlWhenArgumentBlank() {
        final TestableOllamaManagementService service = newService("http://configured-host:22434",
                clientReturningModels(List.of()));

        assertTrue(service.isHealthy(" "));
        assertTrue(service.isHealthy());
        assertEquals("http://configured-host:22434", service.lastResolvedBaseUrl);
    }

    @Test
    void isHealthyUsesRuntimeBaseUrlWhenConfiguredAsActive() {
        final TestableOllamaManagementService service = newService("http://configured-host:22434",
                clientReturningModels(List.of()));
        service.activeRuntimeBaseUrl = "http://runtime-host:22434";
        service.healthStatusByBaseUrl.put("http://runtime-host:22434", 200);

        assertTrue(service.isHealthy(" "));
        assertEquals("http://runtime-host:22434", service.lastResolvedBaseUrl);
    }

    @Test
    void listModelsPrefersExplicitBaseUrlOverRuntimeBaseUrl() {
        final OllamaModelInfo model = new OllamaModelInfo();
        model.setName("gemma4");
        final TestableOllamaManagementService service = newService("http://configured-host:22434",
                clientReturningModels(List.of(model)));
        service.activeRuntimeBaseUrl = "http://runtime-host:22434";

        service.listModels("http://explicit-host:22434");
        assertEquals("http://explicit-host:22434", service.lastResolvedBaseUrl);
    }

    @Test
    void listModelsReturnsEmptyWhenConfiguredBaseUrlMissing() {
        final TestableOllamaManagementService service = newService("   ", clientReturningModels(List.of()));
        assertTrue(service.listModels(" ").isEmpty());
    }

    private static TestableOllamaManagementService newService(
            final String baseUrl,
            final OllamaManagementClient client) {
        final TestableOllamaManagementService service = new TestableOllamaManagementService();
        service.config = configWithBaseUrl(baseUrl);
        service.objectMapper = new ObjectMapper().findAndRegisterModules();
        service.clientToReturn = client;
        service.healthStatusByBaseUrl.put("http://configured-host:22434", 200);
        return service;
    }

    private static OllamaManagementClient clientReturningModels(final List<OllamaModelInfo> models) {
        return (OllamaManagementClient) Proxy.newProxyInstance(
                OllamaManagementClient.class.getClassLoader(),
                new Class<?>[] { OllamaManagementClient.class },
                (proxy, method, args) -> {
                    if ("listModels".equals(method.getName())) {
                        return new OllamaTagsResponse(models);
                    }
                    if ("health".equals(method.getName())) {
                        throw new UnsupportedOperationException("not needed");
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                });
    }

    private static OllamaConfig configWithBaseUrl(final String baseUrl) {
        return (OllamaConfig) Proxy.newProxyInstance(
                OllamaConfig.class.getClassLoader(),
                new Class<?>[] { OllamaConfig.class },
                (proxy, method, args) -> {
                    if ("baseUrl".equals(method.getName())) {
                        return java.util.Optional.ofNullable(baseUrl);
                    }
                    if ("modelName".equals(method.getName())) {
                        return "gemma4";
                    }
                    if ("embeddingModelName".equals(method.getName())) {
                        return "qwen3-embedding";
                    }
                    final Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) {
                        return false;
                    }
                    if (returnType.equals(int.class)) {
                        return 0;
                    }
                    if (returnType.equals(double.class)) {
                        return 0.0d;
                    }
                    if (returnType.equals(Boolean.class)) {
                        return Boolean.FALSE;
                    }
                    if (returnType.equals(Integer.class)) {
                        return Integer.valueOf(0);
                    }
                    return "";
                });
    }

    private static final class TestableOllamaManagementService extends OllamaManagementService {
        private String lastResolvedBaseUrl;
        private String activeRuntimeBaseUrl;
        private OllamaManagementClient clientToReturn;
        private RuntimeException probeFailure;
        private final Map<String, Integer> healthStatusByBaseUrl = new java.util.HashMap<>();
        private final Map<String, List<OllamaModelInfo>> modelsByBaseUrl = new java.util.HashMap<>();
        private final List<String> pulledModels = new java.util.ArrayList<>();

        @Override
        String resolveBaseUrl(final String baseUrl) {
            if (baseUrl != null && !baseUrl.isBlank()) {
                return baseUrl.trim();
            }
            if (activeRuntimeBaseUrl != null && !activeRuntimeBaseUrl.isBlank()) {
                return activeRuntimeBaseUrl;
            }
            return config.baseUrl().orElse(null);
        }

        @Override
        int probeHealthStatus(final String normalizedBaseUrl) throws Exception {
            if (probeFailure != null) {
                throw probeFailure;
            }
            lastResolvedBaseUrl = normalizedBaseUrl;
            return healthStatusByBaseUrl.getOrDefault(normalizedBaseUrl, 500);
        }

        @Override
        OllamaManagementClient buildClient(final String resolvedBaseUrl) {
            lastResolvedBaseUrl = resolvedBaseUrl;
            if (clientToReturn != null) {
                return clientToReturn;
            }
            return clientForBaseUrl(this, resolvedBaseUrl);
        }

        private OllamaManagementClient clientForBaseUrl(
                final TestableOllamaManagementService service,
                final String resolvedBaseUrl) {
            return (OllamaManagementClient) Proxy.newProxyInstance(
                    OllamaManagementClient.class.getClassLoader(),
                    new Class<?>[] { OllamaManagementClient.class },
                    (proxy, method, args) -> {
                        if ("listModels".equals(method.getName())) {
                            final List<OllamaModelInfo> models = service.modelsByBaseUrl
                                    .getOrDefault(resolvedBaseUrl, List.of());
                            return new OllamaTagsResponse(models);
                        }
                        if ("pullModel".equals(method.getName())) {
                            final OllamaPullRequest request = (OllamaPullRequest) args[0];
                            service.pulledModels.add(resolvedBaseUrl + "|" + request.getName());
                            final OllamaModelInfo pulledModel = new OllamaModelInfo();
                            pulledModel.setName(request.getName());
                            pulledModel.setModel(request.getName());
                            service.modelsByBaseUrl
                                    .computeIfAbsent(resolvedBaseUrl, key -> new java.util.ArrayList<>())
                                    .add(pulledModel);
                            return new OllamaPullStatus("success", null, 0, 0);
                        }
                        if (method.getReturnType().equals(boolean.class)) {
                            return false;
                        }
                        return null;
                    });
        }
    }
}
