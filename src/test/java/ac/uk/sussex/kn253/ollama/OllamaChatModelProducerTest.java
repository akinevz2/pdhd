package ac.uk.sussex.kn253.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.*;

import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.services.ModelConfigService;
import dev.langchain4j.model.chat.ChatModel;

class OllamaChatModelProducerTest {

    private LocalOllamaServer endpointA;
    private LocalOllamaServer endpointB;

    @AfterEach
    void tearDown() {
        if (endpointA != null) {
            endpointA.close();
        }
        if (endpointB != null) {
            endpointB.close();
        }
    }

    @Test
    void changingPersistedBaseUrlRoutesLangChainChatModelToNewEndpoint() throws Exception {
        endpointA = LocalOllamaServer.start("reply-from-a");
        endpointB = LocalOllamaServer.start("reply-from-b");

        final LLMSettings settings = new LLMSettings();
        settings.setBaseUrl(endpointA.baseUrl());
        settings.setModelName("gemma4");

        final MutableSettingsModelConfigService modelConfig = new MutableSettingsModelConfigService(settings);

        final OllamaChatModelProducer producer = new OllamaChatModelProducer();
        producer.config = configWithDefaults("http://configured-host:11434");
        producer.modelConfigService = modelConfig;

        final ChatModel chatModelA = producer.produceChatModel();

        final String firstReply = chatModelA.chat("hello");
        assertTrue(firstReply.contains("reply-from-a"));
        assertEquals(1, endpointA.chatCalls());
        assertEquals(0, endpointB.chatCalls());

        settings.setBaseUrl(endpointB.baseUrl());

        final ChatModel chatModelB = producer.produceChatModel();

        final String secondReply = chatModelB.chat("hello again");
        assertTrue(secondReply.contains("reply-from-b"));
        assertEquals(1, endpointA.chatCalls());
        assertEquals(1, endpointB.chatCalls());
    }

    @Test
    void fallsBackToConfiguredBaseUrlWhenPersistedBaseUrlIsBlank() throws Exception {
        endpointA = LocalOllamaServer.start("configured-endpoint");

        final LLMSettings settings = new LLMSettings();
        settings.setBaseUrl(" ");
        settings.setModelName("gemma4");

        final MutableSettingsModelConfigService modelConfig = new MutableSettingsModelConfigService(settings);

        final OllamaChatModelProducer producer = new OllamaChatModelProducer();
        producer.config = configWithDefaults(endpointA.baseUrl());
        producer.modelConfigService = modelConfig;

        final ChatModel chatModel = producer.produceChatModel();
        assertTrue(chatModel.chat("first").contains("configured-endpoint"));
        assertEquals(1, endpointA.chatCalls());
    }

    private static OllamaConfig configWithDefaults(final String baseUrl) {
        return (OllamaConfig) Proxy.newProxyInstance(
                OllamaConfig.class.getClassLoader(),
                new Class<?>[] { OllamaConfig.class },
                (proxy, method, args) -> (switch (method.getName()) {
                    case "baseUrl" -> java.util.Optional.of(baseUrl);
                    case "modelName" -> "gemma4";
                    case "embeddingModelName" -> "qwen3-embedding";
                    case "timeoutSeconds" -> 20;
                    case "temperature" -> 0.1d;
                    case "numPredict" -> 64;
                    case "numCtx" -> 2048;
                    case "enabled" -> true;
                    case "embeddingEnabled" -> Boolean.TRUE;
                    case "embeddingMaxResults" -> Integer.valueOf(5);
                    case "embeddingDimension" -> Integer.valueOf(384);
                    case "ollamaImage" -> "ollama/ollama:latest";
                    default -> {
                        final Class<?> returnType = method.getReturnType();
                        if (returnType.equals(boolean.class)) {
                            yield false;
                        }
                        if (returnType.equals(int.class)) {
                            yield 0;
                        }
                        if (returnType.equals(double.class)) {
                            yield 0.0d;
                        }
                        if (returnType.equals(Boolean.class)) {
                            yield Boolean.FALSE;
                        }
                        if (returnType.equals(Integer.class)) {
                            yield Integer.valueOf(0);
                        }
                        yield "";
                    }
                }));
    }

    private static final class MutableSettingsModelConfigService extends ModelConfigService {
        private final LLMSettings settings;

        private MutableSettingsModelConfigService(final LLMSettings settings) {
            this.settings = settings;
        }

        @Override
        public LLMSettings load() {
            return settings;
        }
    }

    private static final class LocalOllamaServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger chatCalls = new AtomicInteger();

        private LocalOllamaServer(final HttpServer server) {
            this.server = server;
        }

        static LocalOllamaServer start(final String replyText) throws IOException {
            final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            final LocalOllamaServer local = new LocalOllamaServer(server);
            server.createContext("/api/chat", new ChatHandler(local.chatCalls, replyText));
            server.start();
            return local;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        int chatCalls() {
            return chatCalls.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class ChatHandler implements HttpHandler {
        private final AtomicInteger calls;
        private final String replyText;

        private ChatHandler(final AtomicInteger calls, final String replyText) {
            this.calls = calls;
            this.replyText = replyText;
        }

        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            calls.incrementAndGet();

            // Drain request body so the HTTP client can reuse/close the connection cleanly.
            try (InputStream requestBody = exchange.getRequestBody()) {
                requestBody.readAllBytes();
            }

            final byte[] responseBody = ("{" +
                    "\"model\":\"gemma4\"," +
                    "\"created_at\":\"2026-04-09T00:00:00Z\"," +
                    "\"message\":{\"role\":\"assistant\",\"content\":\"" + replyText + "\"}," +
                    "\"done\":true" +
                    "}").getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        }
    }
}