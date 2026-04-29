# Sprint: Tool Dispatch Architecture Rewrite

**Date**: 2026-04-26  
**Spec**: [tool-hallucination-spec.md](evaluation/tool-hallucination-spec.md)  
**Focus**: Implement the explicit tool dispatch boundary defined by the spec — replacing the
LangChain4j-implicit dispatch path with a fully typed, failure-transparent layer.

---

## Motivation

The current build uses LangChain4j's `@Tool`-annotated methods dispatched via an opaque CDI
proxy. When Quarkus AOT compilation mishandles the proxy, the proxy's `toString()` or raw method
descriptor leaks back to the model, which then hallucinates a continuation. There is no
structured failure type at the AI/service boundary, making it impossible to distinguish
AI-layer parse failures from service-layer business failures in logs or HTTP responses.

The spec defines five deliverable sections that together replace the fragile implicit path.

---

## Current State Audit

| Artefact                  | Status     | Notes                                                                                     |
| ------------------------- | ---------- | ----------------------------------------------------------------------------------------- |
| `AiToolsFailureException` | ✅ Exists  | Service-layer accumulator with `FailureMode` enum. Retained.                              |
| `ConversationalException` | ✅ Exists  | Copy constructor, accrued causes. Retained.                                               |
| `AssistantToolRegistry`   | ✅ Exists  | LangChain4j tool-spec injection. Retained.                                                |
| `ProjectKnowledge` entity | ✅ Exists  | Simple key/value. Needs `DependentKey` enum + schema refactor.                            |
| `AiToolCallException`     | ❌ Missing | Low-level AI parse/dispatch failure. Must be created first.                               |
| `ToolDefinition` record   | ❌ Missing | `(name, description, ObjectNode schema)` — Jackson `ObjectNode` (`quarkus-rest-jackson`). |
| `ToolContainer` interface | ❌ Missing | `bashUtil()`, `call()`, `schema()`.                                                       |
| `ToolRegistry`            | ❌ Missing | `@ApplicationScoped`, startup-built, `isKnownTool(name)`.                                 |
| `ToolDispatcher`          | ❌ Missing | Functional, per-request error accumulator, 3-retry schema validation.                     |
| `EmbeddedTokenVectorNode` | ❌ Missing | RAG conversation history vector node.                                                     |
| `parseToolCall(String)`   | ❌ Missing | Lives in `ToolDispatcher`; uses `AiToolCallException`.                                    |
| REST 502 mapper           | ❌ Missing | Maps `AiToolCallException` → HTTP 502 `AI_LAYER_FAILURE`.                                 |

---

## Task Breakdown

### Task A — `AiToolCallException` _(prerequisite for D, F, G)_

**Package**: `ac.uk.sussex.kn253`  
**File**: `AiToolCallException.java`  
**Agent**: PDHD Tools Maintainer

Requirements from spec §5:

- `extends RuntimeException`
- `private final String rawModelOutput` — captured at parse time, never propagated further
- Constructors:
  - `AiToolCallException(String message)`
  - `AiToolCallException(String message, Throwable cause, String rawModelOutput)`
- `String getRawModelOutput()` accessor
- **Distinction from `AiToolsFailureException`**: `AiToolCallException` is the _low-level_
  AI-layer parse/dispatch failure; `AiToolsFailureException` is the _service-layer_ accumulator.
  They are not interchangeable.

---

### Task B — `ToolDefinition` record + `ToolContainer` interface _(prerequisite for C, D)_

**Package**: `ac.uk.sussex.kn253.tools`  
**Agent**: PDHD Tools Maintainer

**`ToolDefinition.java`**:

```java
public record ToolDefinition(String name, String description, ObjectNode schema) {
    public String schemaAsString() { return schema.toString(); }
    public String schemaAsMarkdown() { return MARKDOWN_RENDERER.render(schema, name); }
}
```

**`ToolContainer.java`** (interface):

- `default Optional<String> bashUtil()` → `Optional.empty()`
- `default Optional<ProcessBuilder> call(String... args)` → `Optional.empty()`
- `ToolDefinition schema()` — mandatory

Constraints (applies to all implementing classes):

- No reflections
- No switch statements
- No ternary expressions
- No boxed types

---

### Task C — `ToolRegistry` _(prerequisite for D)_

**Package**: `ac.uk.sussex.kn253.tools`  
**File**: `ToolRegistry.java`  
**Agent**: PDHD Tools Maintainer

- `@ApplicationScoped`
- Collects all `ToolContainer` beans; builds `ToolDefinition` instances once at `@PostConstruct`
- `boolean isKnownTool(String name)`
- `List<ToolDefinition> allTools()`
- Distinct from `AssistantToolRegistry` — that class remains for LangChain4j tool-spec injection

---

### Task D — `ToolDispatcher` + `parseToolCall` _(depends on A, B, C, H)_

**Package**: `ac.uk.sussex.kn253.tools`  
**File**: `ToolDispatcher.java`  
**Agent**: PDHD Tools Maintainer

Requirements from spec §3 and §4:

- All human-readable strings declared as `static final String` constants at file top
- Completely functional — no methods with side effects
- Inject `AnalysisService` _(created in Task H)_, `ToolRegistry`
- Per-request error accumulator: `List<AiToolCallException>` (disallows null items)
- On non-empty accumulator: propagate `ConversationalException` with all accrued exceptions;
  do **not** make further calls to the Ollama REST API
- `parseToolCall(String rawModelOutput)` — exact signature from spec §4:
  - Reads `ObjectNode` (Jackson), extracts `name` + `arguments`
  - Throws `AiToolCallException("Model called unknown tool: " + name)` for unknown tools
  - Catches `JsonException | NullPointerException` → `AiToolCallException("Malformed tool-call output...", e, rawModelOutput)`
- Schema validation dispatch (spec §3):
  - Compare required keys in `ToolContainer` schema vs. keys in model's tool-call request
  - Collect extra keys
  - On any mismatch: send corrective System/Tool-tagged message to model
  - Allow 3 retries; on exhaustion throw `AiToolCallException`
- All throwing methods must have a `try-catch`; no untyped exception propagation

---

### Task E — `ProjectKnowledge` `DependentKey` enum + `EmbeddedTokenVectorNode` _(independent)_

**Package**: `ac.uk.sussex.kn253.repository`  
**Agent**: PDHD Repository Maintainer

`DependentKey` enum variants (spec §1.1.2):

| Variant      | Semantics                                                               |
| ------------ | ----------------------------------------------------------------------- |
| `LABEL`      | Substring of sentence showing pre-embedding phrase as used by the model |
| `CONCRETE`   | From user prompt: `"(a) is (continuation)"` is-a clause                 |
| `RELATIVE`   | From model tool-call or message response: `"(a) is (continuation)"`     |
| `CONTEXTUAL` | Last sentence of the model response that produced this Dependent        |
| `OUTCOME`    | Span of model output identifiable as tool-call requests                 |

`ProjectKnowledge` refactor:

- PK refers to a JSON metadata blob
- Dependents (byte-vector + cached Embedding) keyed by `DependentKey`
- All existing tests must remain green — adapt, do not delete

`EmbeddedTokenVectorNode`:

- Relationships determine augmentations passed as metadata to conversation history vector
- Indexed slots for conversation entries and parallel indexed slots for metadata entries

---

### Task F — Harden `LowLevelProjectAssistant` AI-layer boundary _(depends on A)_

**Package**: `ac.uk.sussex.kn253.services.ai`  
**File**: `LowLevelProjectAssistant.java` _(spec calls this `OllamaAiService`)_  
**Agent**: PDHD Services AI Maintainer

- Locate all AI-layer failures (malformed JSON, unknown tool, bad parameters) currently
  thrown or swallowed anywhere in `services/ai/`
- Replace with `throw new AiToolCallException(...)`
- Raw model output must never propagate into the service layer

---

### Task H — Create `AnalysisService` _(depends on A; prerequisite for D)_

**Package**: `ac.uk.sussex.kn253.services`  
**File**: `AnalysisService.java`  
**Agent**: PDHD Services Maintainer

- New `@ApplicationScoped` class — the business-logic facade that `ToolDispatcher` routes
  dispatched tool calls into
- Knows nothing about AI; receives only parsed `ToolCall` values from `ToolDispatcher`
- All AI-layer failures must arrive as `AiToolCallException`; this class must never see raw
  model output
- Delegates to the existing tool classes (`WorkspaceContextTools`, `ReadFileTools`, etc.)
  for actual execution
- Returns structured results to `ToolDispatcher`

---

### Task I — Create `EmbeddingsService` _(independent)_

**Package**: `ac.uk.sussex.kn253.services.ai`  
**File**: `EmbeddingsService.java`  
**Agent**: PDHD Services AI Maintainer

- New `@ApplicationScoped` class
- Owns the embedding model and domain knowledge
- Spec §1.1.1 places it as a sub-component under `LowLevelProjectAssistant`
- Interface: `List<Float> embed(String text)` and any domain-knowledge retrieval methods
  needed by `RetrievalAugmentedGenerationService`

---

### Task G — REST layer 502 exception mapper _(depends on A)_

**Package**: `ac.uk.sussex.kn253.resources`  
**Agent**: PDHD Resources Maintainer

- Add Quarkus `@ServerExceptionMapper` for `AiToolCallException`
- HTTP 502 response body:
  ```json
  { "errorKind": "AI_LAYER_FAILURE", "detail": "<exception message>" }
  ```
- `AiToolsFailureException` maps to `SERVICE_LAYER_FAILURE` (same shape, separate mapper)

---

## Execution Order

```
A ──► F
A ──► G
A ──► H ──► D (via B ──► C ──► D)
B ──► C ──► D
E (independent)
I (independent)
```

Tasks A, E, and I have no prerequisites and can start immediately.  
Tasks F and G unblock once A is complete.  
Task H unblocks once A is complete.  
Task D unblocks once A + B + C + H are all complete.

---

## Resumption Notes

- If interrupted after **A**: B, E, F, G, H, I can all proceed independently
- If interrupted after **B + C**: only D remains in the dispatch chain (plus H if not done)
- `AiToolsFailureException` is **not** replaced — it is the service-layer accumulator
- `AssistantToolRegistry` is **not** removed — it remains for LangChain4j tool-spec injection
- The `ToolRegistry` in Task C is the **explicit dispatch registry** for the new path only
- `LowLevelProjectAssistant` is never renamed — the spec's `OllamaAiService` label is conceptual only
- `ImplicitContextBuilder` is never renamed — `RetrievalAugmentedGenerationService` is a new class

---

## Mid-Sprint Evidence (2026-04-27)

### Delivery status

| Task | Artefact                                        | Status         | Evidence                                                                                                                                                                                               |
| ---- | ----------------------------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| A    | `AiToolCallException`                           | ✅ Delivered   | `src/main/java/ac/uk/sussex/kn253/AiToolCallException.java` — `extends RuntimeException`, `rawModelOutput` field, two constructors, `getRawModelOutput()` accessor                                     |
| B    | `ToolDefinition` record                         | ✅ Delivered   | `src/main/java/ac/uk/sussex/kn253/tools/ToolDefinition.java` — `record(String name, String description, ObjectNode schema)`, `schemaAsString()`, `schemaAsMarkdown()`                                  |
| B    | `ToolContainer` interface                       | ✅ Delivered   | `src/main/java/ac/uk/sussex/kn253/tools/ToolContainer.java` — `bashUtil()`, `call()`, `schema()`, no reflection/switch/ternary/boxed types                                                             |
| C    | `ToolRegistry`                                  | ✅ Delivered   | `src/main/java/ac/uk/sussex/kn253/tools/ToolRegistry.java` — `@ApplicationScoped`, `@PostConstruct` startup build, `isKnownTool(String)`, `allTools()`                                                 |
| D    | `ToolDispatcher` + `parseToolCall`              | ✅ Delivered   | `src/main/java/ac/uk/sussex/kn253/tools/ToolDispatcher.java` — fully functional, per-request `List<AiToolCallException>` accumulator, 3-retry schema validation, `ConversationalException` propagation |
| H    | `AnalysisService`                               | ✅ Delivered   | `src/main/java/ac/uk/sussex/kn253/services/AnalysisService.java` — `@ApplicationScoped`, AI-agnostic facade, prefix-resolution dispatch, delegates to existing tool classes                            |
| —    | `AiToolsFailureException` refactor              | ✅ Delivered   | Aligned with new exception hierarchy; service-layer accumulator role unchanged                                                                                                                         |
| E    | `DependentKey` enum + `EmbeddedTokenVectorNode` | ❌ Not started | Independent — no blockers                                                                                                                                                                              |
| F    | Harden `LowLevelProjectAssistant` AI boundary   | ❌ Not started | Unblocked since Task A delivered                                                                                                                                                                       |
| G    | REST 502 exception mapper                       | ❌ Not started | Unblocked since Task A delivered                                                                                                                                                                       |
| I    | `EmbeddingsService`                             | ❌ Not started | Independent — no blockers                                                                                                                                                                              |

### Test results

| Suite                 | Tests | Pass | Error | Notes                                                                                                                                                                                                                                                                                                                                             |
| --------------------- | ----- | ---- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ToolDispatcherTest`  | 21    | 20   | 1     | `parseToolCallAccruesErrorForEmptyString` — `ClassCastException` from `MissingNode`→`ObjectNode` cast escapes try-catch when `rawModelOutput` is `""`. Bug: `MAPPER.readTree("")` returns `MissingNode` in current Jackson version; catch block nominally covers `ClassCastException` but exception escapes. Requires targeted fix before Task F. |
| `AnalysisServiceTest` | 26    | 26   | 0     | All dispatch paths, prefix resolution, ambiguity handling, and exception wrapping verified.                                                                                                                                                                                                                                                       |

### Open issues

1. **`parseToolCallAccruesErrorForEmptyString` failure** — `ToolDispatcher.parseToolCall` at line 94: `MAPPER.readTree("")` returns `MissingNode`; unchecked cast to `ObjectNode` throws `ClassCastException` that escapes the catch block. Fix: wrap cast in an additional null/instance check or broaden catch to include `Exception` before propagation.
2. **Tasks E, F, G, I not started** — all are independently deliverable now that Task A is complete. F and G are highest priority (AI-boundary hardening and HTTP error mapping).

### Spec compliance check

| Constraint                                                       | Status                                                                          |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| All human-readable strings as `static final` constants           | ✅ `ToolDispatcher`                                                             |
| Completely functional (no side effects beyond accumulator param) | ✅ `ToolDispatcher`                                                             |
| No switch statements                                             | ✅ All new files                                                                |
| No ternary expressions                                           | ✅ All new files                                                                |
| No boxed types                                                   | ✅ All new files                                                                |
| No reflections in `ToolContainer` implementors                   | ✅ Interface contract stated                                                    |
| All throwing methods have explicit try-catch                     | ⚠️ `parseToolCall` has try-catch but `ClassCastException` escapes (see issue 1) |
| `AiToolsFailureException` not replaced                           | ✅ Retained as service-layer accumulator                                        |
| `AssistantToolRegistry` not removed                              | ✅ Retained for LangChain4j injection                                           |
