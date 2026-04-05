package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.ollama.*;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;

class OllamaManagementServiceTest {

    @Test
    void isHealthyUsesProvidedBaseUrl() {
        final TestableOllamaManagementService service = newService("http://configured-host:11434",
                clientReturningHealth(200));

        assertTrue(service.isHealthy("http://override-host:11434"));
        assertEquals("http://override-host:11434", service.lastResolvedBaseUrl);
    }

    @Test
    void listModelsUsesProvidedBaseUrlAndParsesResponse() {
        final OllamaModelInfo model = new OllamaModelInfo();
        model.setName("llama3.1");
        final TestableOllamaManagementService service = newService("http://configured-host:11434",
                clientReturningModels(List.of(model)));

        final List<OllamaModelInfo> models = service.listModels("http://override-host:11434");
        assertEquals(1, models.size());
        assertEquals("llama3.1", models.get(0).getName());
        assertEquals("http://override-host:11434", service.lastResolvedBaseUrl);
    }

    @Test
    void isHealthyFallsBackToConfiguredBaseUrlWhenArgumentBlank() {
        final TestableOllamaManagementService service = newService("http://configured-host:11434",
                clientReturningHealth(200));

        assertTrue(service.isHealthy(" "));
        assertTrue(service.isHealthy());
        assertEquals("http://configured-host:11434", service.lastResolvedBaseUrl);
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
        return service;
    }

    private static OllamaManagementClient clientReturningHealth(final int status) {
        return (OllamaManagementClient) Proxy.newProxyInstance(
                OllamaManagementClient.class.getClassLoader(),
                new Class<?>[] { OllamaManagementClient.class },
                (proxy, method, args) -> {
                    if ("health".equals(method.getName())) {
                        return jakarta.ws.rs.core.Response.status(status).build();
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                });
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
                        return baseUrl;
                    }
                    if ("modelName".equals(method.getName())) {
                        return "llama3.1";
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
        private OllamaManagementClient clientToReturn;

        @Override
        OllamaManagementClient buildClient(final String resolvedBaseUrl) {
            lastResolvedBaseUrl = resolvedBaseUrl;
            return clientToReturn;
        }
    }
}
