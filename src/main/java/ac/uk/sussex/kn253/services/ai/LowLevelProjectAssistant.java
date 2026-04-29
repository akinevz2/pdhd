package ac.uk.sussex.kn253.services.ai;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.AiToolCallException;
import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.services.ModelConfigService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.TokenStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LowLevelProjectAssistant implements ProjectAssistant {

    private static final Logger LOG = Logger.getLogger(LowLevelProjectAssistant.class);
    private static final String ERR_UNKNOWN_TOOL = "Model called unknown tool: ";
    private static final String ERR_MALFORMED_TOOL_ARGS = "Malformed tool-call arguments from model";

    private final ChatMemoryProvider chatMemoryProvider = new WebUiChatMemoryProviderSupplier().get();

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    AssistantToolRegistry toolRegistry;

    @Inject
    ImplicitContextBuilder implicitContextBuilder;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public TokenStream stream(final String memoryId, final String message) {
        final ManualTokenState state = new ManualTokenState(memoryId, message);
        return (TokenStream) Proxy.newProxyInstance(
                TokenStream.class.getClassLoader(),
                new Class<?>[] { TokenStream.class },
                (proxy, method, args) -> handleTokenStreamInvocation(proxy, method.getName(), args, state));
    }

    private Object handleTokenStreamInvocation(
            final Object proxy,
            final String methodName,
            final Object[] args,
            final ManualTokenState state) {
        return switch (methodName) {
            case "onPartialResponse" -> {
                state.partialResponseHandler = castConsumer(args);
                yield proxy;
            }
            case "onCompleteResponse" -> {
                state.completeResponseHandler = castConsumer(args);
                yield proxy;
            }
            case "onError" -> {
                state.errorHandler = castConsumer(args);
                yield proxy;
            }
            case "onRetrieved" -> {
                state.retrievedHandler = castConsumer(args);
                yield proxy;
            }
            case "onToolExecuted" -> {
                state.toolExecutedHandler = castConsumer(args);
                yield proxy;
            }
            case "ignoreErrors" -> {
                state.ignoreErrors = true;
                yield proxy;
            }
            case "start" -> {
                startStream(state);
                yield null;
            }
            case "toString" -> "ManualTokenStreamProxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
            default -> throw new UnsupportedOperationException("Unsupported TokenStream method: " + methodName);
        };
    }

    @SuppressWarnings("unchecked")
    private Consumer<Object> castConsumer(final Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        return (Consumer<Object>) args[0];
    }

    private void startStream(final ManualTokenState state) {
        final ChatMemory memory = chatMemoryProvider.get(state.memoryId);
        final UserMessage userMessage = UserMessage.from(state.message);
        final List<ChatMessage> persistedMessages = new ArrayList<>(memory.messages());
        final List<ChatMessage> requestMessages = buildRequestMessages(persistedMessages, userMessage);

        if (state.retrievedHandler != null) {
            state.retrievedHandler.accept(List.of());
        }

        final ChatRequest initialRequest = ChatRequest.builder()
                .messages(requestMessages)
                .toolSpecifications(toolRegistry.toolSpecifications())
                .build();

        streamingChatModel.chat(initialRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(final String partialResponse) {
                if (state.partialResponseHandler != null) {
                    state.partialResponseHandler.accept(partialResponse);
                }
            }

            @Override
            public void onCompleteResponse(final ChatResponse response) {
                try {
                    final ChatResponse finalResponse = completeTurnWithToolsIfNeeded(requestMessages, response,
                            state);
                    memory.add(userMessage);
                    if (finalResponse != null && finalResponse.aiMessage() != null) {
                        memory.add(finalResponse.aiMessage());
                    }
                    if (state.completeResponseHandler != null) {
                        state.completeResponseHandler.accept(finalResponse != null ? finalResponse : response);
                    }
                } catch (final Exception e) {
                    handleError(e, state);
                }
            }

            @Override
            public void onError(final Throwable error) {
                handleError(error, state);
            }
        });
    }

    private List<ChatMessage> buildRequestMessages(
            final List<ChatMessage> persistedMessages,
            final UserMessage userMessage) {
        final List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(resolveSystemPrompt()));
        messages.addAll(implicitContextBuilder.buildMessages());
        messages.addAll(persistedMessages);
        messages.add(userMessage);
        return messages;
    }

    private ChatResponse completeTurnWithToolsIfNeeded(
            final List<ChatMessage> baseRequestMessages,
            final ChatResponse initialResponse,
            final ManualTokenState state) {
        if (initialResponse == null || initialResponse.aiMessage() == null) {
            return initialResponse;
        }

        AiMessage aiMessage = initialResponse.aiMessage();
        if (!aiMessage.hasToolExecutionRequests()) {
            return initialResponse;
        }

        final List<ChatMessage> loopMessages = new ArrayList<>(baseRequestMessages);
        ChatResponse latestResponse = initialResponse;

        while (aiMessage != null && aiMessage.hasToolExecutionRequests()) {
            loopMessages.add(aiMessage);

            for (final ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                validateToolRequest(request);
                final String result = toolRegistry.execute(request);
                if (state.toolExecutedHandler != null) {
                    state.toolExecutedHandler.accept(request);
                }
                loopMessages.add(ToolExecutionResultMessage.from(request, result));
            }

            final ChatRequest followUp = ChatRequest.builder()
                    .messages(loopMessages)
                    .toolSpecifications(toolRegistry.toolSpecifications())
                    .build();

            latestResponse = chatModel.chat(followUp);
            aiMessage = latestResponse != null ? latestResponse.aiMessage() : null;
        }

        final String finalText = latestResponse != null && latestResponse.aiMessage() != null
                ? latestResponse.aiMessage().text()
                : null;
        if (finalText != null && !finalText.isBlank() && state.partialResponseHandler != null) {
            state.partialResponseHandler.accept(finalText);
        }

        return latestResponse;
    }

    private String resolveSystemPrompt() {
        final LLMSettings settings = modelConfigService.load();
        final String system = (settings.getSystemPrompt() == null || settings.getSystemPrompt().isBlank())
                ? LLMSettings.DEFAULT_SYSTEM_PROMPT
                : settings.getSystemPrompt();
        final String toolSystem = (settings.getToolSystemPrompt() == null || settings.getToolSystemPrompt().isBlank())
                ? LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT
                : settings.getToolSystemPrompt();
        return system + "\n\n" + toolSystem;
    }

    private void validateToolRequest(final ToolExecutionRequest request) {
        final String name = request.name();
        if (!toolRegistry.registeredToolNames().contains(name)) {
            throw new AiToolCallException(ERR_UNKNOWN_TOOL + name);
        }
        final String arguments = request.arguments();
        if (arguments != null && !arguments.isBlank()) {
            try {
                objectMapper.readTree(arguments);
            } catch (final Exception e) {
                throw new AiToolCallException(ERR_MALFORMED_TOOL_ARGS, e, arguments);
            }
        }
    }

    private void handleError(final Throwable error, final ManualTokenState state) {
        LOG.error("Assistant stream failed", error);
        if (state.ignoreErrors) {
            if (state.completeResponseHandler != null) {
                state.completeResponseHandler.accept(null);
            }
            return;
        }
        if (state.errorHandler != null) {
            state.errorHandler.accept(error);
        }
    }

    private static final class ManualTokenState {

        private final String memoryId;
        private final String message;

        private Consumer<Object> partialResponseHandler;
        private Consumer<Object> completeResponseHandler;
        private Consumer<Object> errorHandler;
        private Consumer<Object> retrievedHandler;
        private Consumer<Object> toolExecutedHandler;
        private boolean ignoreErrors;

        private ManualTokenState(final String memoryId, final String message) {
            this.memoryId = memoryId;
            this.message = message;
        }
    }
}
