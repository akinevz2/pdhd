---
name: pdhd-system-prompt-maintenance
description: "Use when reviewing or rewriting the PDHD LLM system prompts in LLMSettings to reduce model hallucination, improve tool grounding, or tighten assistant scope. Triggers: system prompt review, DEFAULT_SYSTEM_PROMPT update, DEFAULT_TOOL_SYSTEM_PROMPT rewrite, model hallucinating persona or scope, assistant responding out of domain, tool system prompt improvement."
argument-hint: "Which prompt needs updating: DEFAULT_SYSTEM_PROMPT, DEFAULT_TOOL_SYSTEM_PROMPT, or both? Describe the observed misbehaviour."
---

# PDHD System Prompt Maintenance

The PDHD assistant has two persisted prompts stored in `LLMSettings`:

| Constant                     | Purpose                                             |
| ---------------------------- | --------------------------------------------------- |
| `DEFAULT_SYSTEM_PROMPT`      | Defines the assistant persona and operational scope |
| `DEFAULT_TOOL_SYSTEM_PROMPT` | Instructs the model on how and when to call tools   |

These defaults are written to the database on first use via `ModelConfigService#defaults()`. A live instance may have overridden them via the configuration menu — changes to the defaults only affect new installs or a reset.

## When to Use

- The model responds out of scope (e.g. writes code, answers general knowledge questions unrelated to project analysis)
- The model calls tools speculatively or in a loop without grounding
- The model hallucinates tool names or argument formats not present in the registered tool surface
- Benchmark scenarios are failing because the model does not constrain its behavior to the available tools
- You are resetting the system prompts after a model change

## Source Files

| File                                                                         | Role                                                                       |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `src/main/java/ac/uk/sussex/kn253/repository/LLMSettings.java`               | Defines `DEFAULT_SYSTEM_PROMPT` and `DEFAULT_TOOL_SYSTEM_PROMPT` constants |
| `src/main/java/ac/uk/sussex/kn253/services/ModelConfigService.java`          | Applies defaults at first use — `defaults()` method                        |
| `src/main/java/ac/uk/sussex/kn253/services/ai/ProjectAssistantProducer.java` | Wires the prompts into the `AiServices` builder                            |

## Procedure

### 1. Read the current defaults

Read `LLMSettings.java` and note the exact current text of both constants. Do not rewrite from memory.

### 2. Read the registered tool surface

Read `ProjectAssistantProducer` to confirm the current tool class list. The `DEFAULT_TOOL_SYSTEM_PROMPT` must be consistent with the actual tool names — hallucination risk is highest when the prompt mentions tools that are not registered, or omits tools that are.

### 3. Evaluate `DEFAULT_SYSTEM_PROMPT` against the grounding checklist

The system prompt should:

- [ ] Identify the assistant's name (`PDHD`)
- [ ] State the primary task domain (filesystem inspection, project analysis) and explicitly exclude general-purpose chat
- [ ] Instruct the model to prefer tool-retrieved evidence over prior knowledge when answering questions about a project
- [ ] Instruct the model to state when it cannot answer without a tool call rather than guessing
- [ ] Be concise (under 100 words) — verbose system prompts dilute instruction following on small models

### 4. Evaluate `DEFAULT_TOOL_SYSTEM_PROMPT` against the grounding checklist

The tool system prompt should:

- [ ] List the available tool names (or their functional categories) so the model knows what is callable
- [ ] State the path convention: the backend works with absolute paths rooted in open project directories; relative paths are resolved against the current working directory
- [ ] Instruct the model to call tools with the minimum required arguments and to verify the working directory before constructing paths
- [ ] State that when a tool returns a string beginning with `"Error:"` or `"Access denied:"`, the tool call failed and the model must not treat that string as valid data
- [ ] State the maximum number of sequential tool calls before reporting back to the user (prevents spinning)

### 5. Draft replacements

Present the proposed replacement text for each constant before editing. Label clearly which constant each replacement targets.

### 6. Apply changes to `LLMSettings.java`

Edit only `DEFAULT_SYSTEM_PROMPT` and/or `DEFAULT_TOOL_SYSTEM_PROMPT` in `LLMSettings.java`. Do not change any other constant, field, or method in that file.

### 7. Check `ModelConfigService#defaults()`

Confirm the defaults method references `LLMSettings.DEFAULT_SYSTEM_PROMPT` and `LLMSettings.DEFAULT_TOOL_SYSTEM_PROMPT` (not hardcoded strings). If it does, no change needed there.

### 8. Note live-instance caveat

Add a comment in your change summary that existing database rows retain the old prompts until reset. A user must reset their settings via the configuration menu (or a database wipe) to pick up the new defaults.

## Anti-Patterns to Avoid

- **Do not** inline tool implementation details (e.g. "calls `Files.readString`") — describe callable behavior only
- **Do not** list tool Java method names — list the `@Tool(name=...)` dispatch names
- **Do not** add role-play framing ("You are a helpful robot...") that conflicts with the technical grounding instructions
- **Do not** duplicate content between `DEFAULT_SYSTEM_PROMPT` and `DEFAULT_TOOL_SYSTEM_PROMPT` — they are combined by the runtime and redundancy wastes context tokens

## Reference Files

- `CODESTYLE.md` — architecture context for what tools can and cannot do
- `docs/tool-dispatch-precedence.md` — tool naming policy
- `docs/evaluation/benchmark-scenarios.md` — the scenario set that the prompts must support
- `docs/known-issues.md` — recorded hallucination and scope-drift observations
