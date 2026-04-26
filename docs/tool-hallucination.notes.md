# Tool Hallucination — Architecture Specification

When Quarkus's AOT compilation strips or mishandles the proxy, step 3 breaks silently — the CDI
proxy's `toString()` or raw method descriptor leaks back to the model instead of the actual return
value. The model then hallucinates a continuation based on that garbage.

**Advice on addressing this issue, partially implemented in the current build:**

Abandon the annotation-driven magic entirely and own the tool-call lifecycle explicitly. This also
gives you the clean failure boundary you need.

---

## 1. Define the AI/Service boundary clearly

### 1.1 `OllamaAiService` (owns: prompt construction, tool schema, response parsing)

#### 1.1.1 `EmbeddingsService` (owns: embedding model, domain knowledge)

#### 1.1.2 `RetrievalAugmentedGenerationService` (owns: `ProjectKnowledge`, node graph of request embeddings)

RAG should be performed by constructing a `EmbeddedTokenVectorNode` with relationships determining
augmentations to be passed as metadata back to the conversation history vector, which should have
indexed slots for conversation entries, and a parallel sequence of indexed slots for metadata entries.

Metadata entries take the shape of Aliased and Labelled tokens, both Kinds stored into the
`ProjectKnowledge` table.

**TODO — refactor the `ProjectKnowledge` table such that:**

- Primary Key refers to a JSON metadata blob.
- Dependents (of type `ProjectKnowledge`) refer to a byte-vector and its cached Embedding
  representation.
  - Dependents are JSON metadata, but their keys should be rigorously specified in the
    `ProjectKnowledge` Java class as an `Enum` type, with variants:
    `"concrete"`, `"relative"`, `"contextual"`, `"outcome"`, `"label"`.
  - `"label"` — generated from surrounding context of reasoning by the Ollama agent: a substring
    of the sentence showing the pre-embedding byte-vector phrase as used by the model.
  - `"concrete"` — set if the user prompt contains a comma- or period-delimited sentence stating
    `"(a) is (continuation)"`. Updated whenever a new is-a clause is received from user input.
  - `"relative"` — set if the model makes a `"(a) is (continuation)"` clause in its tool-call
    response or user-message response.
  - `"contextual"` — set to the last sentence of the model's response that produced this
    Dependent; every label must have a tagged contextual.
  - `"outcome"` — set to the span of text the model produced that can be identified as
    tool-call requests.

### 1.2 `ToolDispatcher` (owns: routing tool-call requests to services)

- Must be completely functional, with no methods having side effects.
- Must use constant `String` declarations at the top of the file to clearly reflect all
  human-readable logic within the class.
- Must not return any `String`-based or untyped exception types. All throwing methods must contain
  a succinct `try-catch`.
- Must own an injected `AnalysisService`, which should have a method for adding per-request errors.
  The per-request errors datatype must be a linear store that disallows null items, and must
  propagate up to the calling API service an `ac.uk.sussex.kn253.ConversationalException` with all
  accrued exceptions from tool calls, instead of invoking any further communication with the Ollama
  REST API.

### 1.3 `AnalysisService` — Service layer boundary (owns: business logic, knows nothing about AI)

Failures in the AI layer (malformed JSON, unknown tool name, bad parameters) must throw a dedicated
exception `ac.uk.sussex.kn253.AiToolCallException` and never propagate raw model output into the
service layer.

---

## 2. Define tools as plain records/POJOs

The tool object must contain **none** of the following:

- reflections
- switch statements
- ternary expressions
- boxed types

Instead of `@Tool`, implement the `ToolContainer` interface, which specifies:

- Two `Optional<String>` methods with default values of `Optional.empty()`:
  - `Optional<String> bashUtil();`
  - `Optional<ProcessBuilder> call(String... args);`
- A mandatory `ToolDefinition schema()` method.

The tool definition record:

```java
public record ToolDefinition(String name, String description, JsonObject schema) {}
```

- `JsonObject` must be serialised to `String` when passing to the model.
- It must contain fields like `{"exec": "bash -c ls"}`. Keys and commonly-repeated schema
  fragments must be implemented via static factory methods.

**Additional constraints:**

- Build all `ToolDefinition` instances once at startup (e.g., in an `@ApplicationScoped ToolRegistry`).
- Every request to the chat streaming/blocking APIs must include a deserialisation of the tool list
  shaped as an API listing, with instructions to return a single JSON object — no reasoning or plain
  text before or after it — specifying the needed tool as structured in the schema.
- No sanitisation / trimming / normalising / pre-parsing / transformation of data in this layer.
- No retry logic.
- Every failure must be transparent and owned by `ToolDispatcher`, which maintains a new list of
  failures per active user message and returns it to the caller. The happy path — no errors, correct
  tool invocation, or casual conversational message — continues through parent classes to invoke the
  Ollama REST API before streaming/returning the response to the user.

---

## 3. Explicit dispatch in `ToolDispatcher`

```java
@ApplicationScoped
public class ToolDispatcher {

    @Inject
    AnalysisService analysisService;

    public String dispatch(String toolName, JsonObject arguments) {
        return switch (toolName) {
            case "analyse_dataset" -> analysisService.analyse(
                arguments.getString("datasetId"),
                arguments.getInteger("sampleSize")
            );
            case "summarise_results" -> analysisService.summarise(
                arguments.getString("resultId")
            );
            default -> throw new AiToolCallException("Unknown tool: " + toolName);
        };
    }
}
```

No reflection, no proxy issues, fully traceable.

This is a brittle piece of machinery. It must be improved to correctly inspect the `JsonObject`
schema as stored in the `ToolContainer` class, verify that all required keys are present, collect
any extra keys, and — in case of any difference between the schema's key set and the keys in the
model's tool-call request — send the model a corrective non-User message (System or Tool-tagged),
requesting it to amend the call to match the expected schema. Allow **3 retries**; on exhaustion
throw `AiToolCallException` to the Resource layer.

---

## 4. Validate before dispatching

Before calling `dispatch()`, validate the model's tool-call response strictly:

```java
public ToolCall parseToolCall(String rawModelOutput) {
    try {
        JsonObject json = Json.createReader(new StringReader(rawModelOutput)).readObject();
        String name = json.getString("name");         // throws if missing
        JsonObject args = json.getJsonObject("arguments"); // throws if missing
        if (!registry.isKnownTool(name)) {
            throw new AiToolCallException("Model called unknown tool: " + name);
        }
        return new ToolCall(name, args);
    } catch (JsonException | NullPointerException e) {
        throw new AiToolCallException("Malformed tool-call output from model", e, rawModelOutput);
    }
}
```

Capture `rawModelOutput` in the exception so you can log and inspect exactly what the model
produced — invaluable during development.

---

## 5. `AiToolCallException` as the clear failure mode

```java
public class AiToolCallException extends RuntimeException {
    private final String rawModelOutput; // for logging/debugging
    // ...
}
```

The REST layer or orchestration layer catches this specifically and returns a structured error
(e.g., HTTP 502 with a payload distinguishing `AI_LAYER_FAILURE` from `SERVICE_LAYER_FAILURE`),
so upstream callers and logs can immediately identify where the fault originated.
