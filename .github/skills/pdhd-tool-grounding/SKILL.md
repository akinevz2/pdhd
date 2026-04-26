---
name: pdhd-tool-grounding
description: "Use when auditing or improving @Tool and @P annotation descriptions in the PDHD tool layer to reduce LLM hallucination, erratic tool dispatch, or incorrect argument construction. Triggers: tool description audit, benchmark instability, model selecting wrong tool, hallucinated paths, tool annotation review, @P description improvement."
argument-hint: "Which tool class or specific tool method needs grounding improvement? Or: 'audit all tools'."
---

# PDHD Tool Grounding Audit

Erratic model behavior during benchmarks is typically caused by underspecified `@Tool` and `@P` descriptions. This skill guides a systematic audit and rewrite of those descriptions to make tool selection and argument construction unambiguous for the dispatching LLM.

## When to Use

- Model selects the wrong tool for a user request during benchmarks or live use
- Model hallucinates a path or argument that the tool description does not constrain
- Model calls a tool repeatedly with slightly varying arguments (spinning)
- You are adding a new `@Tool` method and want to apply the grounding standard from the outset
- Benchmark S-series scenarios are failing with unexpected tool calls

## Registered Tool Classes

Always verify the current registered set in `ProjectAssistantProducer` before starting. As of 2026-04-26:

| Class                   | Location                                                            |
| ----------------------- | ------------------------------------------------------------------- |
| `ReadFileTools`         | `src/main/java/ac/uk/sussex/kn253/tools/ReadFileTools.java`         |
| `WorkspaceContextTools` | `src/main/java/ac/uk/sussex/kn253/tools/WorkspaceContextTools.java` |
| `WebSearchTools`        | `src/main/java/ac/uk/sussex/kn253/tools/WebSearchTools.java`        |

Producer location: `src/main/java/ac/uk/sussex/kn253/services/ai/ProjectAssistantProducer.java`

## Procedure

### 1. Collect the current tool surface

Read all three tool class files. For each `@Tool` method, record:

- The current `@Tool` description string (or `name` + `value` pair)
- Each `@P` description
- The return type and what it actually returns on success and failure

### 2. Apply the grounding checklist to each `@Tool` description

A well-grounded `@Tool` description must answer all of the following for the dispatching LLM:

| Question                                                       | Why it matters                                     |
| -------------------------------------------------------------- | -------------------------------------------------- |
| What does this tool do in one unambiguous sentence?            | Prevents mis-selection                             |
| When should the model call this tool instead of a similar one? | Prevents wrong-tool dispatch                       |
| What does this tool explicitly NOT do?                         | Prevents over-calling                              |
| What does the tool return on success (format, shape)?          | Helps the model interpret the result               |
| What string does the tool return on failure?                   | Prevents the model treating an error as valid data |

Thin descriptions like `"Run a web search and return top results (title + URL)."` must be expanded to address the checklist.

### 3. Apply the grounding checklist to each `@P` description

Each `@P` must specify:

- **Type/format** — Is it an absolute path, relative path, plain string, integer?
- **Null/blank handling** — What happens if the caller passes blank? Does the tool have a default behaviour?
- **Valid range or constraint** — e.g. `maxResults` should state `1–10` and what the tool does outside that range.
- **Disambiguating example** — For path parameters, state whether absolute or relative is expected, and how the tool resolves relative paths.

### 4. Check explicit `@Tool(name=...)` on every method

Methods without an explicit `name` field use the Java method name as the dispatch name. This creates risk when methods are renamed. Every `@Tool` method must carry an explicit `name`.

### 5. Apply the CODESTYLE constraint check

Per `CODESTYLE.md`: `@Tool` methods must be **thin wrappers**. If any description implies the tool itself does complex logic, validation, or path resolution, verify the logic is in a service. Do not move logic as part of this skill — flag it separately.

### 6. Propose rewrites

For each description that fails the checklist, write a replacement. Present the before/after for review before editing. Only edit once the replacement is confirmed.

### 7. Verify dispatch uniqueness

After any description change, confirm `ProjectAssistantProducer#validateToolNameUniqueness` still covers all tool classes. If a new `@Tool(name=...)` was added that could conflict, check the full name map.

## Anti-Patterns to Avoid

- **Do not** shrink descriptions to save tokens — shorter is not always better for grounding
- **Do not** add implementation details to descriptions (e.g. "calls `Files.readString`") — describe observable behavior, not internals
- **Do not** change the Java method name to fix a dispatch name — use `@Tool(name=...)` instead
- **Do not** add logic inside the `@Tool` method to "help" the model — that belongs in a service

## Reference Files

- `CODESTYLE.md` — architectural rules for the tools package
- `docs/tool-dispatch-precedence.md` — naming policy and startup guard
- `docs/evaluation/benchmark-scenarios.md` — scenario definitions that drive the tool calls
- `docs/known-issues.md` — recorded instability observations
