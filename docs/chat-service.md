# Chat Service Architecture

## Overview

The Chat Service is the primary orchestrator for multi-turn conversational interactions with an LLM-powered assistant. It manages session lifecycle, integrates with tool execution, and coordinates between frontend requests and backend infrastructure.

## Key Components

### ChatService

**Location:** `src/main/java/ac/uk/sussex/kn253/services/ChatService.java`

**Scope:** Application-scoped singleton

**Purpose:**

- Owns the active `OllamaChatSession` instance
- Manages session lifecycle (initialization and reconfiguration)
- Provides the primary entry points for message handling
- Handles settings updates without requiring application restart

**Key Methods:**

- `sendMessage(String message)` - Multi-turn conversation with history
- `sendOneShotMessage(String message)` - Single-turn stateless response
- `reconfigure(OllamaSettings settings)` - Rebuild session with new settings
- `resetConversation()` - Clear conversation history

### OllamaChatSession

**Location:** `src/main/java/ac/uk/sussex/kn253/ollama/OllamaChatSession.java`

**Scope:** Dependent (typically used as singleton via ChatService)

**Purpose:**

- Manages multi-turn conversation state
- Implements the core conversation loop (up to 8 rounds)
- Orchestrates tool execution and result feedback
- Handles system prompt composition and model configuration

**Core Capabilities:**

1. **Conversation History Management**
   - Maintains ordered list of `ChatMessage` instances
   - Supports reset without creating new bean
   - Each turn appends both user input and assistant response

2. **Model Communication**
   - Sends `ChatRequest` to configured Ollama endpoint
   - Receives `ChatResponse` with potential tool calls
   - Supports both native tool-calling models and text-based parsing

3. **Tool Loop Execution**
   - Detects tool execution requests in model response
   - Delegates execution to `ToolService`
   - Appends tool results back to history
   - Repeats conversation loop up to MAX_TOOL_ROUNDS (8)

4. **Context Enrichment**
   - Embeds current working directory via supplier
   - Appends project metadata via supplier
   - System prompt building via `SystemPromptBuilder`

## Request Flow

### Multi-turn Conversation (sendMessage)

```
User Input
    ↓
ChatService.sendMessage()
    ↓
ChatService.directReply() [quick checks]
    ↓
OllamaChatSession.send()
    ↓
SystemPromptBuilder [constructs effective system prompt]
    ↓
LangChain4j ChatModel [sends to Ollama]
    ↓
Response Parsing
    ├─ Extract AiMessage
    └─ Parse tool calls (native or text-based)
    ↓
Tool Loop (up to 8 rounds):
    ├─ ToolService.execute()
    ├─ ToolActivityService.record()
    ├─ Append ToolExecutionResultMessage to history
    └─ Continue loop or return final text
    ↓
Return Assistant Response
```

### One-shot Conversation (sendOneShotMessage)

```
User Input
    ↓
ChatService.sendOneShotMessage()
    ↓
ChatService.directReply() [quick checks]
    ↓
OllamaChatSession.sendOneShot()
    ↓
[Same flow as sendMessage, but history is NOT persisted]
    ↓
Return Response
```

## Settings and Configuration

### OllamaSettings Model

**Location:** `src/main/java/ac/uk/sussex/kn253/model/OllamaSettings.java`

**Key Fields:**

- `baseUrl` - Ollama server endpoint (e.g., `http://localhost:11434`)
- `modelName` - Selected model identifier (e.g., `llama3.2`, `qwen2.5-coder`)
- `temperature` - Sampling randomness (0.0 = deterministic, 1.0+ = creative)
- `numPredict` - Maximum tokens to generate per response
- `numCtx` - Context window size
- `timeoutSeconds` - Request timeout for Ollama API calls
- `systemPrompt` - Custom base system instruction
- `toolSystemPrompt` - Tool-specific system instruction

### Reconfiguration

Changes to settings are applied live via `ChatService.reconfigure(OllamaSettings)`:

```java
// User saves new settings from UI
OllamaSettings newSettings = ollamaConfigService.load();
chatService.reconfigure(newSettings);
// Old session discarded, new session created with new settings
// Conversation history is cleared (use resetConversation() if not)
```

This approach enables users to switch models, endpoints, or parameters without restarting the application.

## Integration Points

### Tool Service Integration

The chat session delegates tool execution to `ToolService`:

```
Tool Execution Request
    ↓
OllamaChatSession.toolService.execute(request, memoryId)
    ├─ Dispatch to appropriate ToolModule
    ├─ Execute ToolMacro
    └─ Return String result
    ↓
Append to history as ToolExecutionResultMessage
```

**Key Contract:**

- Tool results are always returned as strings
- Execution is transactional (single DB transaction per tool)
- Failures are caught and returned as error messages
- No exception propagation to break conversation loop

### System Prompt Building

The `SystemPromptBuilder` composes an effective system prompt by:

1. Prepending base system prompt
2. Appending operating rules and constraints
3. Including tool availability information (if tools enabled)
4. Adding XML tool-call format specification (for non-native models)
5. Injecting current working directory
6. Injecting project metadata (from suppliers)

This ensures the assistant understands available tools and project context without requiring explicit user configuration.

### Activity Tracking

Every tool execution is recorded by `ToolActivityService`:

```
Tool Execution Complete
    ↓
ToolActivityService.recordToolExecution(...)
    ├─ Extract tool name and requested files
    ├─ Add ToolActivity entry to in-memory ring buffer
    └─ Optionally persist to database
    ↓
Activity available via GET /api/tool-activity
```

This provides both real-time debugging and historical audit of assistant behavior.

## Session Lifecycle

### Initialization

1. **On Application Startup:**
   - `ChatService.init()` called (via `@PostConstruct`)
   - Load persisted `OllamaSettings` via `OllamaConfigService.load()`
   - Call `reconfigure(settings)` to build initial session

2. **Session Creation:**
   - Build `OllamaChatModel` from settings
   - Set system prompts
   - Initialize empty conversation history
   - Store suppliers for CWD and metadata

### Reconfiguration

1. **On Settings Change:**
   - UI saves new settings
   - `ChatService.reconfigure(newSettings)` called
   - Old chat session discarded
   - New session built with new settings
   - Conversation history cleared

2. **During Conversation:**
   - History persists across multiple `sendMessage()` calls
   - Call `resetConversation()` to clear history
   - Suppliers are re-evaluated on each turn

### Reset

```java
chatService.resetConversation();
```

Effects:

- Clears conversation history
- Preserves current settings
- Preserves model instance
- Ready for new conversation

## Model Selection and Compatibility

Different models have different capabilities:

### Tool-Native Models

Models like `llama3.2` have native tool support and can generate structured tool calls directly.

- Advantage: Cleaner tool invocation
- Advantage: Reduced prompt engineering
- Requirement: Model must support tool specification format

### Text-Based Models

Models like `qwen2.5-coder` require XML-formatted tool calls parsed from text.

- Mechanism: `ToolCallParser` extracts XML tool calls from model output
- System prompt includes explicit XML format instructions
- Fallback: If parsing fails, return text as-is

### Tool Loop Guard

The session includes guard logic to prevent infinite tool loops:

```
if (toolCallCount > MAX_IDENTICAL_TOOL_CALLS) {
    return ERROR_MESSAGE;
}
```

Different tool categories have different thresholds:

- Default: 4 identical calls
- Summarization tools: 3 identical calls
- Exploration tools: 7 identical calls

This prevents scenarios where the model repeatedly calls the same tool without progress.

## Error Handling

Errors during conversation do not break the loop; they are caught and communicated:

1. **Tool Execution Errors:**
   - Caught by ToolService
   - Returned as error message string
   - Appended to history
   - Conversation continues

2. **Model API Errors:**
   - Timeout: Communicated to user
   - Rate limit: Retried or returned as error
   - Connection error: Returned as error message

3. **Parsing Errors:**
   - Invalid JSON tool arguments: Error message appended to history
   - Unrecognized tool name: Error message appended to history
   - XML parse failure: Fall back to text response

## Performance Considerations

### Context Window Management

The session embeds the full conversation history in each request. For long conversations:

- History grows linearly with message count
- Model context window limits conversation length
- Consider implementing context summarization for very long sessions

### Tool Execution Latency

Tool-using conversations have additional latency per round:

- Model inference time
- Tool execution time (varies by tool)
- Typical tool round: 500ms - 5s
- Multi-round tool sequences can accumulate delay

Strategies to minimize:

- Use `sendOneShotMessage()` for conversations that don't need history
- Reset conversation periodically to trim history
- Optimize tool implementations for speed

### Embedding Integration

When embeddings are used, additional pre-processing occurs:

- User message is embedded (typically 100-500ms for large embeddings)
- Embedding vectors are stored/indexed
- Introspection tools can retrieve similar content
- See [embeddings.md](embeddings.md) for details

## Testing and Debugging

### Direct Java Testing

```java
// Create session without CDI
OllamaChatSession session = new OllamaChatSession(
    "http://localhost:11434",
    "llama3.2"
);
session.setSystemPrompt("You are a helpful assistant.");
String response = session.send("What is 2+2?");
```

### API Testing

```bash
# Start conversation
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What is 2+2?"}'

# One-shot
curl -X POST http://localhost:8080/api/chat/oneshot \
  -H "Content-Type: application/json" \
  -d '{"message":"What is 2+2?"}'

# Reset
curl -X POST http://localhost:8080/api/chat/reset
```

### Debugging Tool Loops

When debugging unexpected tool behavior:

1. Check `GET /api/tool-activity?limit=50` for recent tool calls
2. Verify tool arguments via activity log
3. Check database for side effects (read cache, project knowledge)
4. Examine system prompt via `SystemPromptBuilder`
5. Test tool directly via ToolService

## Future Enhancements

### Streaming Responses

Current implementation returns complete responses. Streaming could:

- Return partial responses as they are generated
- Provide real-time feedback for long operations
- Improve perceived latency

### Context Summarization

Long conversations could benefit from:

- Automatic summarization of old messages
- Token-aware history pruning
- Selective history replay

### Multi-Model Comparison

Could support:

- Side-by-side inference with multiple models
- Model routing based on task type
- Fallback mechanisms for model failures

### Advanced Telemetry

Could track:

- Token usage per conversation
- Tool success/failure rates
- User satisfaction per conversation
- Performance metrics per model

## References

- [Tool Calling Architecture](tool-calling-architecture.md) - How tools are discovered and executed
- [Embeddings Guide](embeddings.md) - Embedding generation and integration
- [API Documentation](frontend.md) - Chat API endpoints
