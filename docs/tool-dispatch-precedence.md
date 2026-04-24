# Tool Dispatch Precedence Policy

This document defines the maintainer policy for tool registration and dispatch
safety in the assistant runtime.

## Scope

Current tool modules wired by the assistant producer:

- ReadFileTools
- WorkspaceContextTools
- WebSearchTools

These are registered in `ProjectAssistantProducer#produceProjectAssistant`.

## Precedence model

LangChain4j tool selection is model-driven, but runtime registration order and
name uniqueness still define the effective dispatch surface.

Policy:

- Every exposed tool method must have a globally unique public tool name.
- Public tool name is resolved as:
  - `@Tool(name=...)` when provided and non-blank.
  - Otherwise method name fallback.
- Duplicate names are forbidden across all registered tool modules.

## Startup guard

`ProjectAssistantProducer#validateToolNameUniqueness` enforces this policy at
startup.

Behavior:

- Scans `@Tool` methods from the configured tool classes.
- Builds the public-name map from annotation/fallback names.
- Throws `IllegalStateException` on any duplicates.
- Logs success with module/tool counts when clean.

## Maintainer update checklist

When adding/changing tools:

1. Add the new tool bean to `produceProjectAssistant`.
2. Add the corresponding class to `validateToolNameUniqueness` class list.
3. Ensure each new `@Tool` name is unique across all modules.
4. Start the app and verify the startup dispatch check log appears.

## Failure modes and response

If startup fails with duplicate tool names:

- Rename one conflicting tool (annotation name preferred over method rename).
- Re-run startup until the dispatch guard passes.
- Record the change in `docs/evaluation/decision-log.md` when dispatch policy
  or naming conventions are changed.
