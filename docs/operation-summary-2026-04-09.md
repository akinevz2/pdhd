# Operation Summary (2026-04-09)

## Scope of Changes

This update removes all Testcontainers auto-provisioning and model-pull logic from the Ollama startup path. Both the pre-CDI bootstrap class and the CDI-era startup coordinator now perform a single startup function: verify that the configured Ollama endpoint is reachable before Quarkus CDI initialization. Model provisioning at startup is no longer performed by the application.

---

## 1. Pre-CDI Bootstrap Simplification

**File**: `src/main/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrap.java`

### Before

`PreCdiOllamaBootstrap.prepareForLaunch()` resolved endpoint health, optionally started a Testcontainers Ollama container as fallback, ensured required chat and embedding models were pulled, and set multiple system properties including `pdhd.ollama.bootstrap.models-ready=true`.

### After

`PreCdiOllamaBootstrap.prepareForLaunch()` performs three steps only:

1. Load the `pdhd.ollama.base-url` value from `application.properties`, system properties, or environment variables via `BootstrapConfig.load()`.
2. If the URL is absent, throw `IllegalStateException("Ollama base URL is not configured")`.
3. Probe `GET <baseUrl>/api/tags` via `DefaultOps.isHealthy(baseUrl)`; if the response is not HTTP 200, throw `IllegalStateException("Configured Ollama endpoint is unreachable: <baseUrl>")`.

On success, `pdhd.ollama.base-url` and `pdhd.ollama.bootstrap.base-url` are written as system properties. No container is started; no models are pulled; no `bootstrapOnStart` flag is involved.

**Stable public entry point** (unchanged signature):

```java
public static void prepareForLaunch(final String[] args)
static void prepareForLaunch(final String[] args, final StartupOps ops)  // testable overload
```

**Health probe implementation** (`DefaultOps.isHealthy`):

```java
final String endpoint = normalizeBaseUrl(baseUrl) + "/api/tags";
final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
return response.statusCode() == 200;
```

---

## 2. Startup Coordinator Simplification

**File**: `src/main/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinator.java`

### Before

`OllamaStartupCoordinator.prepare(commandName)` invoked model-availability checks and pull operations through `OllamaManagementService`, switching the runtime base URL to a Testcontainers-backed endpoint when the configured external endpoint was unhealthy.

### After

`OllamaStartupCoordinator.prepare(commandName)` performs:

1. Load the persisted base URL from `ModelConfigService.load()`.
2. Resolve the active base URL via `OllamaRuntimeEndpointService.resolvePersistedOrActive(persistedBaseUrl)`.
3. Probe health via `OllamaManagementService.isHealthy(baseUrl)`.
4. On success, register the resolved URL via `OllamaRuntimeEndpointService.setRuntimeBaseUrl(baseUrl)`.

Failure semantics are retained from the previous implementation:

- For any command other than `configure`: throws `IllegalStateException` if the base URL is unconfigured or unhealthy.
- For the `configure` command: logs a warning and returns without throwing, permitting the user to reconfigure.

No model provisioning is performed; no Testcontainers instance is started.

---

## 3. `OllamaConfig` — `bootstrapOnStart` Property Removed

**File**: `src/main/java/ac/uk/sussex/kn253/ollama/OllamaConfig.java`

The `bootstrapOnStart()` method was removed from the `@ConfigMapping` interface. Quarkus strict config mapping fails startup if a mapped key is present in properties without a corresponding accessor method, so the `pdhd.ollama.bootstrap-on-start` key was also removed from all property files. The remaining mapped properties in `OllamaConfig` are:

| Method                  | Config key                          | Default                |
| ----------------------- | ----------------------------------- | ---------------------- |
| `enabled()`             | `pdhd.ollama.enabled`               | `true`                 |
| `baseUrl()`             | `pdhd.ollama.base-url`              | _(none)_               |
| `modelName()`           | `pdhd.ollama.model-name`            | `gemma4`               |
| `embeddingModelName()`  | `pdhd.ollama.embedding-model-name`  | `qwen3-embedding`      |
| `timeoutSeconds()`      | `pdhd.ollama.timeout-seconds`       | `120`                  |
| `temperature()`         | `pdhd.ollama.temperature`           | `0.7`                  |
| `numPredict()`          | `pdhd.ollama.num-predict`           | `-1`                   |
| `numCtx()`              | `pdhd.ollama.num-ctx`               | `0`                    |
| `embeddingEnabled()`    | `pdhd.ollama.embedding-enabled`     | `true`                 |
| `embeddingMaxResults()` | `pdhd.ollama.embedding-max-results` | `5`                    |
| `embeddingDimension()`  | `pdhd.ollama.embedding-dimension`   | `384`                  |
| `ollamaImage()`         | `pdhd.ollama.image`                 | `ollama/ollama:latest` |

---

## 4. Base URL Standardised to `host.docker.internal:11434`

The configured Ollama endpoint is now uniformly `http://host.docker.internal:11434` across all profiles and test configurations. Previous dev-profile entries (e.g. `http://desktop-minifridge:11434`) are replaced.

**Evidence** — `src/main/resources/application.properties`:

```properties
%dev.pdhd.ollama.base-url=http://host.docker.internal:11434
pdhd.ollama.base-url=http://host.docker.internal:11434
```

**Evidence** — `src/test/resources/application-test.properties`:

```properties
pdhd.ollama.base-url=http://host.docker.internal:11434
```

---

## 5. Model Name Changed to `gemma4`

All references to `llama3.1` and `llama3.2` are replaced with `gemma4` across application properties, test properties, and any test fixtures that assert on model-name values.

**Evidence** — `src/main/resources/application.properties`:

```properties
%dev.pdhd.ollama.model-name=gemma4
pdhd.ollama.model-name=gemma4
quarkus.langchain4j.ollama.chat-model.model-id=${pdhd.ollama.model-name}
```

**Evidence** — `src/test/resources/application-test.properties`:

```properties
pdhd.ollama.model-name=gemma4
```

The `OllamaConfig.modelName()` accessor carries `@WithDefault("gemma4")`, making `gemma4` the compile-time default for the entire application.

---

## 6. LangChain4j Devservices Disabled

`quarkus.langchain4j.ollama.devservices.enabled=false` is present in `application.properties`, confirming that the Quarkus LangChain4j extension's own automatic Ollama provisioning is also disabled. Combined with the removal of the application-level Testcontainers bootstrap, the application makes no attempt to auto-provision Ollama under any profile.

---

## 7. Test Suite: Updated Tests

### `PreCdiOllamaBootstrapTest`

**File**: `src/test/java/ac/uk/sussex/kn253/bootstrap/PreCdiOllamaBootstrapTest.java`

The previous test suite (5 tests covering Testcontainers container startup, model pulling, and production confirmation prompts) was replaced by three focused unit tests:

| Test method                                    | Scenario                                           | Expected outcome                                                                          |
| ---------------------------------------------- | -------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `failsWhenBaseUrlNotConfigured`                | `pdhd.ollama.base-url` system property absent      | `IllegalStateException` with "not configured"                                             |
| `failsWhenBaseUrlUnreachable`                  | URL present but `StubOps` does not mark it healthy | `IllegalStateException` with "unreachable"                                                |
| `succeedsAndSetsSystemPropertiesWhenReachable` | URL present and marked healthy by `StubOps`        | Both `pdhd.ollama.base-url` and `pdhd.ollama.bootstrap.base-url` set in system properties |

Tests use a package-private `StubOps` (implements `PreCdiOllamaBootstrap.StartupOps`) whose `healthyBaseUrls` set controls which URLs are deemed reachable — no network I/O occurs.

### `OllamaStartupCoordinatorTest`

**File**: `src/test/java/ac/uk/sussex/kn253/services/OllamaStartupCoordinatorTest.java`

The previous six-test suite (covering Testcontainers fallback, model pulling, and endpoint switching) was replaced by five focused unit tests:

| Test method                                         | Scenario                               | Expected outcome                       |
| --------------------------------------------------- | -------------------------------------- | -------------------------------------- |
| `prepareSetsRuntimeUrlWhenEndpointReachable`        | Configured URL healthy                 | Runtime base URL set to configured URL |
| `prepareThrowsWhenEndpointUnreachable`              | Configured URL unhealthy               | `IllegalStateException` thrown         |
| `prepareThrowsWhenBaseUrlNotConfigured`             | Resolution throws (no URL configured)  | `IllegalStateException` thrown         |
| `prepareConfigureContinuesWhenEndpointUnreachable`  | `configure` command, URL unhealthy     | No exception thrown                    |
| `prepareConfigureContinuesWhenBaseUrlNotConfigured` | `configure` command, resolution throws | No exception thrown                    |

All collaborators are replaced by stub inner classes: `StubModelConfigService`, `StubOllamaManagementService`, and `StubRuntimeEndpointService`. Field injection is exercised directly via package-visible fields on `OllamaStartupCoordinator`.

---

## Recommended Next Steps

1. Update `docs/assistant-request-flow.md` and `docs/april-2026-feature-summary.md` to remove references to Testcontainers bootstrap and model provisioning at startup.
2. Confirm whether `OllamaTestcontainersService` is still referenced by any non-test code paths; if not, schedule removal.
3. Verify that the `pdhd.ollama.image` config key (still present in `OllamaConfig`) does not imply container startup capability to future contributors — add a comment or remove if unused.
