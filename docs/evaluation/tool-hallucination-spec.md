# Tool Hallucination — Architecture Specification

# Overview

> For human maintainers only. The agent must begin work at Section 1 and proceed sequentially.
> After completing each numbered section, output only: "✓ Section N complete" and nothing else
> unless blocked.

## Background

When Quarkus's AOT compilation strips or mishandles the proxy, step 3 breaks silently — the CDI
proxy's `toString()` or raw method descriptor leaks back to the model instead of the actual return
value. The model then hallucinates a continuation based on that garbage.

This specification addresses the issue and is partially implemented in the current build.

## Component Ownership

| Component                              | Owns                                                    |
|----------------------------------------|---------------------------------------------------------|
| `OllamaAiService`                      | Prompt construction, tool schema, response parsing      |
| `EmbeddingsService`                    | Embedding model, domain knowledge                       |
| `RetrievalAugmentedGenerationService`  | `ProjectKnowledge`, node graph of request embeddings    |
| `ToolDispatcher`                       | Routing tool-call requests to services                  |
| `AnalysisService`                      | Business logic; knows nothing about AI                  |

## Rationale

- Metadata entries take the shape of Aliased and Labelled tokens, both Kinds stored into the
  `ProjectKnowledge` table.
- The `ToolDispatcher` dispatch implementation in Section 3 is brittle in its current form and
  must be replaced per the specification below.
- `rawModelOutput` must be stored in `AiToolCallException` to support inspection during development.
- Distinguishing `AI_LAYER_FAILURE` from `SERVICE_LAYER_FAILURE` in HTTP error responses allows
  upstream callers and logs to immediately identify fault origin.

---

**Agent: begin work at Section 1.**

---

## 1. Define the AI/Service boundary

### 1.1 `OllamaAiService`

#### 1.1.1 `EmbeddingsService`

#### 1.1.2 `RetrievalAugmentedGenerationService`

Construct a `EmbeddedTokenVectorNode` with relationships determining augmentations to be passed as
metadata back to the conversation history vector, which must have indexed slots for conversation
entries, and a parallel sequence of indexed slots for metadata entries.

Refactor the `ProjectKnowledge` table such that:

- Primary Key refers to a JSON metadata blob.
- Dependents (of type `ProjectKnowledge`) refer to a byte-vector and its cached Embedding
  representation.
  - Specify Dependent keys rigorously in the `ProjectKnowledge` Java class as an `Enum` type,
    with variants: `"concrete"`, `"relative"`, `"contextual"`, `"outcome"`, `"label"`.
  - `"label"` — generate from surrounding context of reasoning by the Ollama agent: a substring
    of the sentence showing the pre-embedding byte-vector phrase as used by the model.
  - `"concrete"` — set if the user prompt contains a comma- or period-delimited sentence stating
    `"(a) is (continuation)"`. Update whenever a new is-a clause is received from user input.
  - `"relative"` — set if the model makes a `"(a) is (continuation)"` clause in its tool-call
    response or user-message response.
  - `"contextual"` — set to the last sentence of the model's response that produced this
    Dependent; every label must have a tagged contextual.
  - `"outcome"` — set to the span of text the model produced that can be identified as
    tool-call requests.

### 1.2 `ToolDispatcher`

- Implement as completely functional, with no methods having side effects.
- Declare all human-readable logic as constant `String` values at the top of the file.
- All throwing methods must contain a succinct `try-catch`. No method may throw `String`-based or
  untyped exception types.
- Inject `AnalysisService`. Implement a method for adding per-request errors. The per-request
  errors datatype must be a linear store that disallows null items, and must propagate up to the
  calling API service an `ac.uk.sussex.kn253.ConversationalException` with all accrued exceptions
  from tool calls, instead of invoking any further communication with the Ollama REST API.

### 1.3 `AnalysisService`

Throw `ac.uk.sussex.kn253.AiToolCallException` for all AI layer failures (malformed JSON, unknown
tool name, bad parameters). Never propagate raw model output into the service layer.

---

## 2. Define tools as plain records/POJOs

Implement the `ToolContainer` interface. The tool object must contain none of the following:

- reflections
- switch statements
- ternary expressions
- boxed types

The `ToolContainer` interface must specify:

- Two `Optional<String>` methods with default values of `Optional.empty()`:
  - `Optional<String> bashUtil();`
  - `Optional<ProcessBuilder> call(String... args);`
- A mandatory `ToolDefinition schema()` method.

Define the tool definition record:

```java
public record ToolDefinition(String name, String description, JsonObject schema) {}
```

- Serialise `JsonObject` to `String` when passing to the model.
- Include fields such as `{"exec": "bash -c ls"}`. Implement keys and commonly-repeated schema
  fragments via static factory methods.
- Build all `ToolDefinition` instances once at startup in an `@ApplicationScoped ToolRegistry`.
- Every request to the chat streaming/blocking APIs must include a deserialisation of the tool
  list shaped as an API listing, with instructions to the model to return a single JSON object —
  no reasoning or plain text before or after it — specifying the needed tool as structured in the
  schema.
- No sanitisation, trimming, normalising, pre-parsing, or transformation of data in this layer.
- No retry logic.
- Every failure must be transparent and owned by `ToolDispatcher`, which maintains a new list of
  failures per active user message and returns it to the caller. The happy path — no errors,
  correct tool invocation, or casual conversational message — continues through parent classes to
  invoke the Ollama REST API before streaming/returning the response to the user.

---

## 3. Implement `ToolDispatcher` dispatch with schema validation

Inspect the `JsonObject` schema as stored in the `ToolContainer`. Verify that all required keys
are present. Collect any extra keys. In case of any difference between the schema's key set and
the keys in the model's tool-call request, send the model a corrective non-User message (System
or Tool-tagged), requesting it to amend the call to match the expected schema. Allow 3 retries;
on exhaustion throw `AiToolCallException` to the Resource layer.

---

## 4. Validate before dispatching

Implement `parseToolCall(String rawModelOutput)`:

```java
public ToolCall parseToolCall(String rawModelOutput) {
    try {
        JsonObject json = Json.createReader(new StringReader(rawModelOutput)).readObject();
        String name = json.getString("name");
        JsonObject args = json.getJsonObject("arguments");
        if (!registry.isKnownTool(name)) {
            throw new AiToolCallException("Model called unknown tool: " + name);
        }
        return new ToolCall(name, args);
    } catch (JsonException | NullPointerException e) {
        throw new AiToolCallException("Malformed tool-call output from model", e, rawModelOutput);
    }
}
```

Store `rawModelOutput` in `AiToolCallException`.

---

## 5. Implement `AiToolCallException`

```java
public class AiToolCallException extends RuntimeException {
    private final String rawModelOutput;
    // ...
}
```

Catch `AiToolCallException` at the REST layer and return a structured HTTP 502 error payload
distinguishing `AI_LAYER_FAILURE` from `SERVICE_LAYER_FAILURE`.
