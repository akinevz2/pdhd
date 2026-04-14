# Targeted Completion Fix: Ollama Fallback Model Provisioning

## Status

~~Implemented (April 2026).~~ **Superseded (2026-04-09).**

> The Testcontainers fallback and model-provisioning logic described in this document was removed on 2026-04-09. Both `PreCdiOllamaBootstrap` and `OllamaStartupCoordinator` now perform a health-check only. See [docs/operation-summary-2026-04-09.md](operation-summary-2026-04-09.md) for the current implementation. The remainder of this file is retained for historical context.

## Purpose

This document defines a focused fix to complete the runtime fallback workflow:
when PDHD switches to internal Testcontainers Ollama, required models should be
automatically available before assistant requests execute.

## Problem Statement

The previous gap (missing startup model provisioning on fallback) has been
closed by introducing pre-CDI bootstrap initialization and endpoint-aware model
ensuring logic.

Implemented outcome:

- Required models are ensured before Quarkus CDI runtime comes online.
- Production profile can run without a configured external Ollama base URL,
  with terminal confirmation before internal-container fallback.
- Development profile uses the machine-specific `%dev` endpoint default
  (`http://desktop-minifridge:11434`) unless explicitly overridden.

## Target Behavior

Current target behavior now implemented:

1. Resolve required model set from persisted settings:
   - `modelName` (chat)
   - `embeddingModelName` (embedding), if configured
2. For each required model, verify availability against the active runtime
   endpoint.
3. Pull missing models automatically.
4. Continue startup only after model availability checks complete.
5. Record clear startup logs for model checks and pulls.

For `configure` command, preserve non-blocking behavior on hard failures:
continue with warning if fallback or model provisioning fails.

## Implemented Change Set

## 1) Pre-CDI startup model-provisioning step

Primary files:

- `src/main/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrap.java`
- `src/main/java/ac/uk/sussex/kn253/PdhdLauncher.java`
- `src/main/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinator.java`

Implemented changes:

- Added pre-CDI bootstrap invocation in launcher main.
- Pre-CDI bootstrap resolves endpoint health, optionally prompts in production,
  starts Testcontainers fallback when needed, and ensures configured models
  before Quarkus starts.
- Startup coordinator now recognizes bootstrap properties and uses them as the
  active runtime endpoint verification source.

Suggested method shape:

```java
private void ensureRequiredModelsAvailable(String activeBaseUrl, String commandName)
```

Behavior:

- Load persisted settings once.
- Build distinct model list from chat + embedding names.
- For each model:
  - Use a baseUrl-aware availability check.
  - Pull only when missing.

## 2) baseUrl-aware ensure helper in management service

Primary file:

- `src/main/java/ac/uk/sussex/kn253/services/OllamaManagementService.java`

Implemented changes:

- Add overloads so startup can target the active runtime endpoint explicitly:
  - `isModelAvailable(String baseUrl, String modelName)`
  - `ensureModelAvailable(String baseUrl, String modelName)`

These methods are now used by startup orchestration and tests.

## 3) Failure semantics

- For non-`configure` commands:
  - If endpoint is healthy but required model pull fails, fail startup with a
    clear error message.
- For `configure` command:
  - Log warning and continue (matches existing startup fallback tolerance).

## 4) Telemetry/logging

- Emit startup logs for:
  - Selected runtime provider and endpoint.
  - Per-model availability result.
  - Pull start/finish and failures.

## Test Coverage

Add targeted tests for startup completion behavior.

### Startup/bootstrap tests

Added file:

- `src/test/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrapTest.java`
- `src/test/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinatorTest.java`

Covered cases include:

1. Production profile with no external base URL prompts and starts internal
   fallback when confirmed.
2. Production profile declines prompt -> startup aborts.
3. Development profile uses `%dev` endpoint without production prompt.
4. Startup coordinator model ensuring and failure semantics remain validated.

### Service tests

Extended:

- `src/test/java/ac/uk/sussex/kn253/services/OllamaManagementServiceTest.java`

Covered cases:

- `isModelAvailable(baseUrl, model)` uses explicit endpoint.
- `ensureModelAvailable(baseUrl, model)` pulls only when missing.

## Acceptance Criteria

The fix is complete and all conditions now hold:

1. Startup fallback to internal Ollama occurs as before when external is
   unreachable.
2. Required chat model is guaranteed available before first assistant request.
3. Configured embedding model (when present) is also guaranteed available.
4. No unnecessary pulls when models are already present.
5. `configure` command remains resilient and non-blocking on fallback/model
   provisioning failures.
6. Automated tests cover success and failure paths for the new behavior,
   including production and development profile startup paths.

## Out of Scope

- Changing UI workflows for manual model management.
- Introducing asynchronous background pull queues.
- Revising model-selection policy beyond configured chat/embedding names.

## Documentation Follow-up

Updated:

- `docs/assistant-request-flow.md` now documents pre-CDI startup behavior,
  production confirmation prompt semantics, and model availability guarantees.
