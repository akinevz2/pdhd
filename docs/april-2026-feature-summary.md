# PDHD Q2 2026 Feature Summary: Startup Resilience, Progress Visibility, and Error Handling

**Date**: April 9, 2026  
**Status**: Complete (all implementations verified)

> **Revision note (2026-04-09)**: Features 1, 2, and 3 described below were subsequently superseded. On 2026-04-09 the Testcontainers auto-provisioning, model-pull logic, and associated startup-progress heartbeat code were removed from both `PreCdiOllamaBootstrap` and `OllamaStartupCoordinator`. The startup path was simplified to a health-check only. The test suites for those two classes were rewritten accordingly. See [docs/operation-summary-2026-04-09.md](operation-summary-2026-04-09.md) for the authoritative description of the current startup implementation.

---

## Executive Summary

This report documents four closely coupled feature implementations completed in April 2026. Features 1, 2, and 3 are now historical because the startup path was simplified on 2026-04-09; Feature 4 remains current.

1. **Testcontainers Fallback for Empty/Unavailable Ollama URLs** _(superseded 2026-04-09)_ — automatic internal Docker container provisioning when the configured Ollama endpoint is missing or unreachable
2. **Live Ollama Workstation Integration Tests** _(superseded 2026-04-09)_ — test suite improvements to fail (not skip) when required models are unavailable, ensuring live environment validation
3. **Startup Progress Visibility** _(superseded 2026-04-09)_ — explicit logging and heartbeat intervals during container startup and model pulls, resolving silent blocking issues
4. **Frontend Error Handling and Chat-Log Hiding** — generic error display mechanism for operation failures with automatic chat log suppression

Taken together, these changes document the original April 2026 work while preserving the current startup behavior and the still-current error-handling behavior.

---

## Feature 1: Pre-CDI Ollama Bootstrap _(current implementation)_

This section now reflects the implementation that is actually in the codebase.

### Entry Point

**Files**:

- [src/main/java/ac/uk/sussex/kn253/PdhdLauncher.java](src/main/java/ac/uk/sussex/kn253/PdhdLauncher.java)
- [src/main/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrap.java](src/main/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrap.java)

`PdhdLauncher.main()` conditionally invokes `PreCdiOllamaBootstrap.prepareForLaunch(args)` before `Quarkus.run(...)`. The bootstrap path is used for normal launches such as the default `webui` flow, while help/version recovery paths are bypassed.

### Current Behavior

`PreCdiOllamaBootstrap.prepareForLaunch()` performs the following steps only:

1. Skip bootstrap for help/version commands.
2. Load `pdhd.ollama.base-url` through `BootstrapConfig.load()` using this precedence:

- system property `pdhd.ollama.base-url`
- environment variable `PDHD_OLLAMA_BASE_URL`
- profile-specific property `%<profile>.pdhd.ollama.base-url`
- unqualified `pdhd.ollama.base-url`

3. Throw `IllegalStateException("Ollama base URL is not configured")` if the value is absent or blank.
4. Probe `GET <baseUrl>/api/tags` and require HTTP 200.
5. On success, set `pdhd.ollama.base-url` and `pdhd.ollama.bootstrap.base-url` as system properties.

The bootstrap does not start containers, prompt the user, or provision models.

### Validation Evidence

**Test File**: [src/test/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrapTest.java](src/test/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrapTest.java)

| Test Case                                      | Scenario                        | Expected Outcome                                    |
| ---------------------------------------------- | ------------------------------- | --------------------------------------------------- |
| `failsWhenBaseUrlNotConfigured`                | `pdhd.ollama.base-url` absent   | `IllegalStateException` containing "not configured" |
| `failsWhenBaseUrlUnreachable`                  | URL present, endpoint unhealthy | `IllegalStateException` containing "unreachable"    |
| `succeedsAndSetsSystemPropertiesWhenReachable` | URL present and healthy         | Both system properties set to configured URL        |

## Feature 2: Live Ollama Workstation Integration Tests _(superseded 2026-04-09)_

> **Status: Superseded.** The `OllamaStartupCoordinatorTest` described below was rewritten on 2026-04-09 to reflect the simplified health-check-only coordinator. The current test suite contains 5 tests with no model-pull or Testcontainers logic. See [docs/operation-summary-2026-04-09.md](operation-summary-2026-04-09.md) for the current test table.

### Objective

Implement integration tests that validate endpoint switching and model availability checking against a live workstation Ollama instance, with **tests failing (not skipping)** when required models are unavailable. This ensures live environment readiness is explicitly verified rather than silently bypassed.

### Architectural Context

Integration tests operate in a different validation context than unit tests:

- **Unit tests**: Mock/stub all external dependencies (e.g., Ollama endpoints).
- **Integration tests**: Require actual external resources (e.g., live workstation Ollama).

By failing tests when prerequisites are unmet (rather than skipping), the test suite makes prerequisite violations explicit in CI/CD pipelines and local development, preventing silent test suite gaps.

### Implementation Details

#### Live Workstation Test Suite

**File**: [src/test/java/ac/uk/sussex/kn253/services/OllamaWorkstationIntegrationTest.java](src/test/java/ac/uk/sussex/kn253/services/OllamaWorkstationIntegrationTest.java)

The test class uses JUnit 5 `Assumptions` to enforce prerequisites:

```java
Assumptions.assumeTrue(isRequiredHost(baseUrl),
    () -> "Live workstation tests require host " + REQUIRED_HOST + " but got " + baseUrl);
Assumptions.assumeTrue(ollamaManagementService.isHealthy(baseUrl),
    () -> "Ollama is unreachable at " + baseUrl);
Assumptions.assumeTrue(!models.isEmpty(),
    () -> "No models are available on live Ollama host: " + baseUrl);
```

**Key contract**: When any assumption fails, the **entire test fails** (not skipped). This signals that the live environment is not ready.

#### Endpoint Validation

Prerequisites verify:

1. **Configured endpoint targets required host**: `baseUrl` must resolve to `host.docker.internal` (Docker Desktop internal network).
2. **Endpoint is healthy**: HTTP 200 response from `/api/tags`.
3. **Models are available**: At least one model is listed; configured chat model or override model is present.

#### Model Selection Strategy

Model selection supports three options:

1. **System property override** (`ollama.live.model`): Highest priority, used in CI/CD.
2. **Configured model** (`pdhd.ollama.model-name`): Used if system property not set.
3. **First available model**: Fallback if neither above is present.

#### Live Chat Test

```java
@Test
void chatReturnsNonEmptyResponseFromLiveModel() {
    final String prompt = "Reply with exactly one short sentence that contains the word integration.";
    final String response = assistant.chat(prompt);

    assertNotNull(response, "Model response should not be null");
    assertFalse(response.isBlank(), "Model response should not be blank");
    assertTrue(response.toLowerCase(Locale.ROOT).contains("integration"),
        () -> "Expected response to include 'integration' but got: " + response);
}
```

The test exercises the full chat chain: LangChain4j model builder → Ollama HTTP API → model inference. Failures indicate real-world chat broken (not mock-layer issues).

### Startup Coordinator Integration Tests (as of supersession, 2026-04-09)

> The original 6-test suite was replaced. The current file contains 5 tests. See [docs/operation-summary-2026-04-09.md](operation-summary-2026-04-09.md) for the current test table.

**File**: [src/test/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinatorTest.java](../src/test/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinatorTest.java)

Current test cases (5 tests):

| Test Case                                           | Scenario                        | Expected Outcome               |
| --------------------------------------------------- | ------------------------------- | ------------------------------ |
| `prepareSetsRuntimeUrlWhenEndpointReachable`        | Configured URL healthy          | Runtime base URL set           |
| `prepareThrowsWhenEndpointUnreachable`              | Configured URL unhealthy        | `IllegalStateException` thrown |
| `prepareThrowsWhenBaseUrlNotConfigured`             | Resolution throws               | `IllegalStateException` thrown |
| `prepareConfigureContinuesWhenEndpointUnreachable`  | `configure` + unhealthy URL     | No exception                   |
| `prepareConfigureContinuesWhenBaseUrlNotConfigured` | `configure` + resolution throws | No exception                   |

_(Original 6-test Testcontainers-era table omitted — superseded.)_

### Validation Evidence

Tests verify:

- **Health-check on reachable endpoint**: Runtime base URL is set when healthy.
- **Fail-fast on unreachable endpoint**: `IllegalStateException` for normal commands.
- **Fail-fast when unconfigured**: `IllegalStateException` for normal commands.
- **Permissive configure path**: Both failure cases continue without exception for the `configure` command.

---

## Feature 3: Startup Progress Visibility _(superseded 2026-04-09)_

> **Status: Superseded.** The implementation described in this section — startup heartbeat logging for Testcontainers container startup and Ollama model pulls — was removed on 2026-04-09 together with Feature 1. Neither `OllamaTestcontainersService` nor the model-pull heartbeat helpers exist in the current codebase. The subsections below are retained for historical reference.

### Objective

Eliminate silent blocking during startup by emitting explicit progress logs and heartbeat messages during long-running operations: Testcontainers container startup and Ollama model pulls. This resolves the issue where `quarkus:dev` would hang with no indication of progress.

### Architectural Context

Long-running I/O operations (container startup, multi-GB model downloads) can appear to hang from the operator's perspective. By emitting periodic heartbeat messages to logs, operators receive confirmation that progress is ongoing, even if no new status is available from the underlying API.

### Implementation Details

#### Container Startup Progress

**File**: [src/main/java/ac/uk/sussex/kn253/services/OllamaTestcontainersService.java](src/main/java/ac/uk/sussex/kn253/services/OllamaTestcontainersService.java)

The `startAndGetEndpoint()` method wraps container startup with progress reporting:

```java
final Instant startTime = Instant.now();
LOG.warning(() -> String.format(
    "Configured Ollama endpoint is unreachable. Starting Testcontainers Ollama image '%s'.",
    config.ollamaImage()));

runWithProgress(
    () -> candidate.start(),
    "Still starting Testcontainers Ollama image '%s'...",
    config.ollamaImage());

final long startupSeconds = Duration.between(startTime, Instant.now()).toSeconds();
LOG.warning(() -> String.format(
    "Testcontainers Ollama image '%s' started in %ds.",
    config.ollamaImage(),
    startupSeconds));
```

**Progress Mechanism**:

The `runWithProgress(Runnable action, String template, String detail)` helper:

1. Creates a daemon thread that wakes every **15 seconds**.
2. If the action is still running, emits a warning log with the message template.
3. Upon action completion, sets a flag and interrupts the progress thread.

**Example log output**:

```
[WARNING] Configured Ollama endpoint is unreachable. Starting Testcontainers Ollama image 'ollama/ollama:latest'.
[WARNING] Still starting Testcontainers Ollama image 'ollama/ollama:latest'...
[WARNING] Still starting Testcontainers Ollama image 'ollama/ollama:latest'...
[WARNING] Testcontainers Ollama image 'ollama/ollama:latest' started in 45s.
```

#### Model Pull Progress

**File**: [src/main/java/ac/uk/sussex/kn253/services/OllamaManagementService.java](src/main/java/ac/uk/sussex/kn253/services/OllamaManagementService.java)

The `ensureModelAvailable(baseUrl, modelName)` method combines three progress mechanisms:

1. **Initial warning** with model name and endpoint:

   ```java
   LOG.warning(() -> String.format(
       "Model '%s' not found locally at %s. Pulling now (this can take several minutes).",
       modelName, resolvedBaseUrl));
   ```

2. **Heartbeat warnings** (15-second interval via `runWithProgressWarnings()`):

   ```java
   "Still pulling Ollama model '%s' from %s ..."
   ```

3. **Completion log** with elapsed time:
   ```java
   final long elapsedSeconds = Duration.between(pullStart, Instant.now()).toSeconds();
   LOG.warning(() -> String.format(
       "Completed pull attempt for model '%s' from %s in %ds with status '%s'.",
       modelName, resolvedBaseUrl, elapsedSeconds, pullStatus.getStatus()));
   ```

**Example log output**:

```
[WARNING] Model 'gemma4' not found locally at http://desktop:11434. Pulling now (this can take several minutes).
[WARNING] Still pulling Ollama model 'gemma4' from http://desktop:11434 ...
[WARNING] Still pulling Ollama model 'gemma4' from http://desktop:11434 ...
[WARNING] Still pulling Ollama model 'gemma4' from http://desktop:11434 ...
[WARNING] Completed pull attempt for model 'gemma4' from http://desktop:11434 in 180s with status 'success'.
```

#### Progress Thread Implementation

Both services use the same pattern:

```java
private void runWithProgress(final Runnable action, final String progressMessageTemplate, final String detail) {
    final AtomicBoolean done = new AtomicBoolean(false);
    final Thread progressThread = new Thread(() -> {
        while (!done.get()) {
            try {
                Thread.sleep(15000L);  // 15-second heartbeat
                if (!done.get()) {
                    LOG.warning(() -> String.format(progressMessageTemplate, detail));
                }
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }, "ollama-model-pull-progress");
    progressThread.setDaemon(true);
    progressThread.start();

    try {
        action.run();
    } finally {
        done.set(true);
        progressThread.interrupt();
    }
}
```

**Key design**:

- **15-second interval**: Balances responsiveness (operator sees progress) with log noise.
- **Daemon thread**: Threads terminate cleanly on JVM shutdown.
- **AtomicBoolean**: Thread-safe completion flag.
- **Finally block**: Ensures progress thread cleanup even if action throws.

### Validation Evidence

- Container startup logs emit initial message + heartbeat messages + completion with elapsed time.
- Model pull logs emit initial message + heartbeat every 15 seconds during pull + completion with elapsed time and pull status.
- Operator receives continuous feedback during startup, resolving silent-hanging perception.

---

## Feature 4: Frontend Error Handling and Chat-Log Hiding

### Objective

Implement a generic error display mechanism for operation failures (e.g., `summarize-folder` errors) that:

1. Displays error messages in the dedicated `assistant-unreachable-notice` area
2. Hides the chat log while an error is displayed
3. Does not trigger automatic retry on errors
4. Allows users to dismiss errors via an X button

This improves error visibility and prevents cascading failures when operations fail.

### Architectural Context

The frontend (React/TypeScript) uses a state-based approach:

- **`assistantUnreachable`**: Boolean flag for general "assistant unavailable" state (e.g., Ollama process down).
- **`chatBlockingError`**: String field for specific operation errors (e.g., folder summary request failed).

The `ChatDock` component renders a unified error notice area that displays either flag's message:

```typescript
{(assistantUnreachable || chatBlockingError) && (
  <div className="assistant-unreachable-notice">
    <span>{chatBlockingError || "Assistant is unreachable..."}</span>
    <button onClick={() => {
        setAssistantUnreachable(false);
        setChatBlockingError(null);
    }}>X</button>
  </div>
)}
```

When either flag is set, the chat log is hidden via conditional render:

```typescript
{!chatBlockingError && (
  <div className="chat-log" ref={chatLogRef}>
    // Chat messages rendered here
  </div>
)}
```

### Implementation Details

#### Frontend: App.tsx

**File**: [src/main/webui/src/App.tsx](src/main/webui/src/App.tsx)

The `openFolderSummary()` function implements error handling:

```typescript
const openFolderSummary = useCallback(
  async (windowId: number, projectDirectory: string, relativePath: string) => {
    // ... setup code ...

    try {
      setChatMessages((prev) => [
        ...prev,
        { role: "user", content: `Summarise folder: ${folderPath}` },
      ]);

      const MAX_ATTEMPTS = 3;
      let result: AssistantChatResponse | undefined;
      let lastErr: unknown;

      for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
        if (attempt > 0) {
          await new Promise((r) => setTimeout(r, 1000 * 2 ** (attempt - 1)));
        }
        try {
          result = await emitApiSignal<
            { path: string },
            AssistantChatResponse
          >(SIGNALS.CHAT_SUMMARIZE_FOLDER, { path: folderPath }, ...);
          break;
        } catch (err) {
          lastErr = err;
          // Don't retry on 4xx client errors
          if (err instanceof Error && /^4\d\d /.test(err.message)) break;
        }
      }

      if (!result) throw lastErr;

      // ... success handling ...
      setAssistantUnreachable(false);

    } catch (err) {
      const detail = err instanceof Error ? err.message : "Unknown error";
      setChatBlockingError(`Error: ${detail}`);  // ← Set blocking error

      updateWindow(windowId, {
        fileLoading: false,
        fileLoadingFolderSummary: false,
        fileContentMarkdown: false,
        fileError: `Failed to summarize folder contents: ${detail}`,
      });
    } finally {
      setChatLoading(false);
    }
  },
  [updateWindow],
);
```

**Error flow**:

1. Attempt folder summarization with exponential backoff (up to 3 attempts).
2. On client errors (4xx), break immediately (no retry).
3. On failure: capture error detail, set `chatBlockingError`, update window state with error.
4. Chat loading flag cleared to allow user interactions.

#### Chat Message Handler: websocket.ts

**File**: [src/main/webui/src/websocket.ts](src/main/webui/src/websocket.ts)

The streaming chat handler also sets `assistantUnreachable` on errors:

```typescript
onError: (detail) => {
  errorOccurred = true;
  setAssistantUnreachable(true);
  setChatError(detail);
  setRetryMessage(message);
  // ... update chat message with error ...
},
```

This handles real-time chat failures (e.g., Ollama connection lost).

#### Frontend: ChatDock Component

**File**: [src/main/webui/src/components/ChatDock.tsx](src/main/webui/src/components/ChatDock.tsx)

The error notice and chat log rendering:

```typescript
{(assistantUnreachable || chatBlockingError) && (
  <div className="assistant-unreachable-notice">
    <span>
      {chatBlockingError ||
        "Assistant is unreachable - check that Ollama is running and the model is loaded."}
    </span>
    <button
      className="notice-dismiss"
      onClick={() => {
        setAssistantUnreachable(false);
        setChatBlockingError(null);
      }}
      aria-label="Dismiss"
    >
      X
    </button>
  </div>
)}

{!chatBlockingError && (
  <div className="chat-log" ref={chatLogRef}>
    {/* Chat messages rendered here */}
  </div>
)}
```

**Behavior**:

- When `chatBlockingError` is set: error notice displayed, chat log hidden.
- When `assistantUnreachable` is set (but `chatBlockingError` is null): error notice displayed, chat log visible.
- Clicking X clears both flags, allowing user to attempt recovery.

### User Experience Flow

**Scenario**: Folder summarization fails

1. User clicks "Explore" on a folder.
2. Frontend sends `SIGNALS.CHAT_SUMMARIZE_FOLDER` request.
3. Backend returns error (e.g., HTTP 500, timeout).
4. Frontend catches error, calls `setChatBlockingError("Error: ...")`.
5. **Chat dock UI**:
   - Error notice appears with message and X button.
   - Chat log hides (no confusion with prior messages).
   - User can read error, dismiss via X, and attempt other actions.
6. User clicks X, both flags cleared, UI returns to normal state.

### Validation Evidence

- `openFolderSummary()` test scenarios:
  - Success case: Sets `assistantUnreachable(false)`, updates window with summary.
  - Error case: Sets `chatBlockingError(...)`, updates window with error detail.
  - Client error case: Breaks retry loop on 4xx status.
- Chat message handler test:
  - On token stream: Appends to message content.
  - On error: Sets `assistantUnreachable(true)`, captures error detail.
  - On completion: Clears flags (unless error occurred).

---

## Cross-Feature Integration

These four features work together in a coordinated flow:

### Startup Path (current — health-check only)

> _(The Testcontainers fallback and model-pull branches shown in the original diagram were removed on 2026-04-09. The current startup path is shown below.)_

```
Application launch
  ↓
PreCdiOllamaBootstrap.prepareForLaunch()
  ├─ Load pdhd.ollama.base-url (system property → env var → application.properties)
  ├─ If absent → throw IllegalStateException("Ollama base URL is not configured")
  ├─ Probe GET <baseUrl>/api/tags
  └─ If HTTP 200 → set pdhd.ollama.base-url and pdhd.ollama.bootstrap.base-url system properties
      If not 200  → throw IllegalStateException("Configured Ollama endpoint is unreachable: <baseUrl>")
     ↓
     Quarkus CDI comes online (services initialized with verified healthy endpoint)
```

### Operation Error Handling (Feature 4)

```
User triggers folder summarization
  ↓
Frontend: openFolderSummary()
  ├─ Attempt operation (up to 3 times with backoff)
  └─ On failure:
     ├─ Call setChatBlockingError(error.message)
     ├─ (Feature 4) Error notice displayed
     ├─ (Feature 4) Chat log hidden
     └─ (Feature 4) User can dismiss via X or retry with corrected parameters
```

### Integration Test Validation (Feature 2)

```
CI/CD pipeline runs OllamaWorkstationIntegrationTest
  ├─ Verify live Ollama endpoint is configured (host.docker.internal)
  ├─ Verify endpoint is healthy
  ├─ Verify required model is available
  │  └─ (Feature 2) If any check fails: TEST FAILS (not skipped)
  └─ Run live chat test
     └─ (Feature 2) Verifies endpoint switching and model availability work in real environment
```

---

## Limitations and Residual Risks

### Known Limitations

1. **Pre-CDI Bootstrap Timing**: The bootstrap must complete before Quarkus CDI starts. If the Ollama endpoint is slow to respond, developers may perceive system unresponsiveness on first startup. Mitigation: ensure the Ollama endpoint is healthy before launching PDHD.

2. **Frontend Error State Recovery**: Once `chatBlockingError` is set, the only recovery path is dismissing via X button. If the underlying error is transient, users must manually retry operations. Consider future work: automatic retry UI for transient errors.

### Residual Risks

- **Model Availability Changes During Runtime**: Models could be deleted from the Ollama instance after startup verification but before first use. Mitigation: This is an operator concern; applications should be restarted if models are deleted.

- **Frontend-Backend Error Desynchronization**: If the backend error occurs but network latency delays the error message, users may see transient state confusion. Mitigation: Error notice and chat log hiding prevent incorrect interpretation of prior messages.

---

## Operational Guidance

### Development Workflow

1. **With Configured External Ollama**:

- Set `pdhd.ollama.base-url` to a reachable Ollama endpoint for the active profile.
- The checked-in defaults are `%dev=http://host.docker.internal:11434` and the unqualified default `http://localhost:11434`.
- Ensure required models are pre-pulled on the external instance.
- Startup will verify endpoint health; no model provisioning is performed.

2. **Without Configured External Ollama**:
   - Leave `pdhd.ollama.base-url` unset or empty in `%dev` profile.
   - Startup will fail with error: "Ollama base URL is not configured".
   - For local testing, configure external Ollama and set `pdhd.ollama.base-url` accordingly.

### Production Deployment

1. **Pre-Stage Ollama**:
   - Deploy Ollama service externally and set `pdhd.ollama.base-url` in production config.
   - PDHD requires a reachable Ollama endpoint at startup; it does not provision containers automatically.

2. **Model Provisioning**:
   - Ensure required models are pre-pulled on the Ollama instance before starting PDHD.
   - PDHD does not pull models automatically; it only verifies endpoint reachability at startup.

3. **Error Recovery**:
   - If folder summarization fails, users will see error notice in chat dock.
   - UI will remain responsive; users can dismiss error and attempt other operations.
   - Root cause is available in server logs and error notice message.

---

## Summary of Changes by File

| Component | File Path                                                                                                                                                          | Change     | Purpose                                                                                                   |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------- | --------------------------------------------------------------------------------------------------------- |
| Bootstrap | [src/main/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrap.java](src/main/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrap.java)                     | New        | Pre-CDI endpoint health verification (health-check only; no container provisioning or model pulls)        |
| Services  | [src/main/java/ac/uk/sussex/kn253/services/OllamaManagementService.java](src/main/java/ac/uk/sussex/kn253/services/OllamaManagementService.java)                   | Extended   | Historical endpoint-aware model checks; bootstrap no longer invokes model availability or pull operations |
| Services  | ~~src/main/java/ac/uk/sussex/kn253/services/OllamaTestcontainersService.java~~                                                                                     | Removed    | Removed 2026-04-09 — Testcontainers container-startup service no longer exists                            |
| Services  | [src/main/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinator.java](src/main/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinator.java)                 | Refactored | Runtime startup validation aligned with the health-check-only bootstrap path                              |
| Tests     | [src/test/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrapTest.java](src/test/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrapTest.java)             | New        | Bootstrap success and failure paths                                                                       |
| Tests     | [src/test/java/ac/uk/sussex/kn253/services/OllamaWorkstationIntegrationTest.java](src/test/java/ac/uk/sussex/kn253/services/OllamaWorkstationIntegrationTest.java) | New        | Live endpoint and model availability tests                                                                |
| Tests     | [src/test/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinatorTest.java](src/test/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinatorTest.java)         | Refactored | Startup coordinator reachability and `configure`-command tolerance scenarios                              |
| Tests     | [src/test/java/ac/uk/sussex/kn253/services/OllamaManagementServiceTest.java](src/test/java/ac/uk/sussex/kn253/services/OllamaManagementServiceTest.java)           | Extended   | BaseUrl-aware model availability and provisioning tests                                                   |
| Frontend  | [src/main/webui/src/App.tsx](src/main/webui/src/App.tsx)                                                                                                           | Enhanced   | Error handling in `openFolderSummary()` sets `chatBlockingError` on failures                              |
| Frontend  | [src/main/webui/src/components/ChatDock.tsx](src/main/webui/src/components/ChatDock.tsx)                                                                           | Enhanced   | Conditional rendering of error notice and chat log based on `chatBlockingError`                           |

---

## References

- [Architecture Overview](docs/architecture.md) — CDI and lifecycle structure
- [Ollama Fallback Completion Fix](docs/ollama-fallback-completion-fix.md) — Earlier design specification for fallback logic
- [Operation Summary (2026-04-09)](docs/operation-summary-2026-04-09.md) — Current startup simplification and bootstrap behavior
- [Frontend Documentation](docs/frontend.md) — React/TypeScript UI architecture
