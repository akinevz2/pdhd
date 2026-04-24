# Chat Service and Ollama Runtime

## Status

This document describes the current Ollama-backed runtime in the checked-in code.
Earlier revisions referred to `ChatService`, `OllamaChatSession`, and
`OllamaConfigService`; those classes are not present in the current source tree
and should not be treated as the active implementation.

## Current Components

### `OllamaConfig`

**Location:** `src/main/java/ac/uk/sussex/kn253/ollama/OllamaConfig.java`

Typed configuration mapping for the `pdhd.ollama.*` namespace.

Current mapped settings are:

- `pdhd.ollama.base-url`
- `pdhd.ollama.model-name`
- `pdhd.ollama.embedding-model-name`
- `pdhd.ollama.timeout-seconds`
- `pdhd.ollama.temperature`
- `pdhd.ollama.num-predict`
- `pdhd.ollama.num-ctx`
- `pdhd.ollama.embedding-enabled`
- `pdhd.ollama.embedding-max-results`
- `pdhd.ollama.embedding-dimension`

### `LLMSettings`

**Location:** `src/main/java/ac/uk/sussex/kn253/repository/LLMSettings.java`

Persisted runtime row storing the active Ollama endpoint, chat model,
embedding model, prompts, and the cached JSON returned from model discovery.

### `ModelConfigService`

**Location:** `src/main/java/ac/uk/sussex/kn253/services/ModelConfigService.java`

Loads the single persisted `LLMSettings` row, creates defaults from
`OllamaConfig` when no row exists, and refreshes the cached model list through
`OllamaManagementService`.

### `OllamaChatModelProducer`

**Location:** `src/main/java/ac/uk/sussex/kn253/ollama/OllamaChatModelProducer.java`

Produces the application `ChatModel` and `StreamingChatModel` beans.

Current behavior:

- Loads persisted `LLMSettings` on production.
- Resolves `baseUrl` from persisted settings first, then from
  `pdhd.ollama.base-url`.
- Resolves `modelName` from persisted settings first, then from
  `pdhd.ollama.model-name`.
- Applies `temperature`, `numPredict`, `numCtx`, and `timeoutSeconds` from
  `OllamaConfig`.
- Enables `RESPONSE_FORMAT_JSON_SCHEMA` for the non-streaming chat model.

### `OllamaManagementService` and `OllamaManagementClient`

**Locations:**

- `src/main/java/ac/uk/sussex/kn253/services/OllamaManagementService.java`
- `src/main/java/ac/uk/sussex/kn253/ollama/OllamaManagementClient.java`

Management operations are split between a small REST-client interface and a
service that resolves the active base URL and builds clients dynamically.

Current management capabilities include:

- endpoint health checks
- model listing
- running-model listing
- model inspection (`/api/show`)
- model pull, streaming pull, and delete
- `ensureModelAvailable(...)` for explicit provisioning flows

## Configuration Resolution

The current runtime contract is:

1. `ModelConfigService.load()` returns the persisted `LLMSettings` row or
   creates one from `OllamaConfig` defaults.
2. `OllamaChatModelProducer` uses persisted `baseUrl` and `modelName` when they
   are non-blank.
3. If persisted values are blank, producer code falls back to `OllamaConfig`.
4. `OllamaManagementService` accepts an explicit base URL when supplied; if not,
   it falls back to `pdhd.ollama.base-url`.

The checked-in property defaults are currently:

| Scope           | Property                           | Value                               |
| --------------- | ---------------------------------- | ----------------------------------- |
| dev profile     | `pdhd.ollama.base-url`             | `http://host.docker.internal:11434` |
| default profile | `pdhd.ollama.base-url`             | `http://localhost:11434`            |
| default profile | `pdhd.ollama.model-name`           | `gemma4:latest`                     |
| default profile | `pdhd.ollama.embedding-model-name` | `qwen3-embedding`                   |
| default profile | `pdhd.ollama.timeout-seconds`      | `300`                               |

The checked-in test profile currently uses:

| Scope        | Property                           | Value                          |
| ------------ | ---------------------------------- | ------------------------------ |
| test profile | `pdhd.ollama.base-url`             | `http://ws-vision.local:11434` |
| test profile | `pdhd.ollama.model-name`           | `gemma4:latest`                |
| test profile | `pdhd.ollama.embedding-model-name` | `qwen3-embedding:latest`       |

## Runtime Flow

The current Ollama runtime flow is narrower than older architecture notes:

```text
application.properties / environment
    ↓
OllamaConfig
    ↓
ModelConfigService.load()
    ↓
LLMSettings persisted row
    ↓
OllamaChatModelProducer
    ├─ produces ChatModel
    └─ produces StreamingChatModel
    ↓
LangChain4j model calls to the resolved Ollama endpoint
```

Model-management flow is handled separately:

```text
explicit baseUrl argument or pdhd.ollama.base-url
    ↓
OllamaManagementService.resolveBaseUrl(...)
    ↓
RestClientBuilder → OllamaManagementClient
    ↓
/api/tags, /api/ps, /api/show, /api/pull, /api/delete
```

## Cached Model Discovery

`ModelConfigService.refreshModelCache()` is the current bridge between the
persisted settings row and live Ollama discovery.

Behavior:

1. Read the existing cached model JSON from `LLMSettings`.
2. Check endpoint health with `OllamaManagementService.isHealthy(baseUrl)`.
3. If the endpoint is unhealthy, return cached values unchanged.
4. If the endpoint is healthy, fetch live models and overwrite the cached JSON.

This means the current implementation preserves a last-known model list rather
than failing every consumer when Ollama is temporarily unavailable.

## What Is Not Current

The following claims from older revisions are no longer accurate for the
checked-in code:

- A `ChatService` class owning an `OllamaChatSession`
- An `OllamaChatSession` conversation loop in `ac.uk.sussex.kn253.ollama`
- An `OllamaConfigService` that synchronizes runtime system properties
- A settings-reconfigure API built around `ChatService.reconfigure(...)`

Any future work that reintroduces those concepts should be documented as a new
implementation, not inferred from this file.
