## Status

Previous revisions of this document described a planned embedding pipeline built
around `ChatService`, `OllamaChatSession`, `EmbeddingService`, and a dedicated
embedding store. Those classes and flows are not present in the current source
tree. The current Ollama package participates in embeddings through
configuration, persisted model selection, and model-management utilities only.

## Current Implementation Surface

### `OllamaConfig`

**Location:** `src/main/java/ac/uk/sussex/kn253/ollama/OllamaConfig.java`

Embedding-related config currently exposed by the package:

| Method                  | Property                            | Default           |
| ----------------------- | ----------------------------------- | ----------------- |
| `embeddingModelName()`  | `pdhd.ollama.embedding-model-name`  | `qwen3-embedding` |
| `embeddingEnabled()`    | `pdhd.ollama.embedding-enabled`     | `true`            |
| `embeddingMaxResults()` | `pdhd.ollama.embedding-max-results` | `5`               |
| `embeddingDimension()`  | `pdhd.ollama.embedding-dimension`   | `384`             |

### `LLMSettings`

**Location:** `src/main/java/ac/uk/sussex/kn253/repository/LLMSettings.java`

The persisted runtime row stores `embeddingModelName` alongside the active chat
model, base URL, prompts, and cached model metadata.

### `ModelConfigService`

**Location:** `src/main/java/ac/uk/sussex/kn253/services/ModelConfigService.java`

On first load, the service seeds the persisted row from `OllamaConfig`, which
means the configured embedding model becomes part of the saved runtime state.

### `OllamaManagementService`

**Location:** `src/main/java/ac/uk/sussex/kn253/services/OllamaManagementService.java`

Management operations are model-agnostic. In practice, this means the same
service can list, pull, inspect, and ensure chat models or embedding models,
provided the caller supplies the relevant model name.

## Configuration Notes

The checked-in defaults relevant to embeddings are currently:

| File                                             | Property                           | Value                    |
| ------------------------------------------------ | ---------------------------------- | ------------------------ |
| `src/main/resources/application.properties`      | `pdhd.ollama.embedding-model-name` | `qwen3-embedding`        |
| `src/test/resources/application-test.properties` | `pdhd.ollama.embedding-model-name` | `qwen3-embedding:latest` |

There is no separate `pdhd.ollama.embedding-base-url` property in the current
package. Embedding-related model management uses the same Ollama base URL as the
rest of the runtime.

## What The Current Package Does Not Implement

The following items were described in older drafts but are not implemented in
the current code:

- a dedicated `EmbeddingService`
- a first-class embedding vector store in `ac.uk.sussex.kn253.ollama`
- automatic per-message embedding generation
- semantic retrieval injected back into prompts by this package
- a separate embedding endpoint configuration property

Any future document that introduces those capabilities should be treated as a
new implementation plan rather than inferred from the current codebase.

## Practical Maintenance Guidance

For the current implementation, the safe assumptions are:

1. The active embedding model name is configuration- and persistence-driven.
2. Model-management operations can be used for embedding models in the same way
   as chat models.
3. Embedding enablement, result limits, and nominal dimension are configuration
   values, not evidence of an active embedding pipeline inside this package.

An agent-only training system could be constructed on top of the existing
project-summary, embedding, and retrieval pipeline. However, that capability is
outside the scope of the present project, which currently focuses on
inspection, persistence, retrieval, and evidence-grounded inference over
externally provided models.

## References

- `docs/chat-service.md` for the current runtime model-resolution flow
- `src/main/java/ac/uk/sussex/kn253/ollama/OllamaConfig.java`
- `src/main/java/ac/uk/sussex/kn253/services/ModelConfigService.java`
- `src/main/java/ac/uk/sussex/kn253/services/OllamaManagementService.java`

Embeddings work alongside existing read cache in `ProjectKnowledge`:

```
Read Tool Execution
    ↓
    ├─ Store in ProjectKnowledge (read cache)
    │   - Key: "file:src/Main.java"
    │   - Value: Full file contents
    │
    └─ [NEW] Generate and store embeddings
        - Chunk content if needed
        - Generate embedding vectors
        - Store in EmbeddingStore with metadata
```

**Relationship:**

- `ProjectKnowledge`: Stores actual content (can be large)
- `EmbeddingStore`: Stores vectors + snippets (optimized for search)
- Both are indexed by source ID for cross-referencing

## API Endpoints

### Log Embedding Operations

```properties
quarkus.log.level=INFO
quarkus.log.category."ac.uk.sussex.kn253.services.EmbeddingService".level=DEBUG
```

## Future Enhancements

### Vector Indexing

For very large embeddings (10,000+), implement:

- HNSW (Hierarchical Navigable Small World) index
- Approximate nearest neighbor search
- Sub-linear search time

### Cross-Project Embeddings

Allow searching embeddings:

- Across multiple projects
- With project-level scoping
- For knowledge transfer between projects

### Embedding-Based Tool Selection

Use embeddings to:

- Recommend tools based on query similarity
- Route user messages to optimal tools
- Automatically detect tool context

### Fine-tuning

Support custom embedding models:

- Train embeddings on project-specific vocabulary
- Optimize for domain-specific information retrieval
- Store project-scoped embedding models

An agent-only training system could be constructed on top of the existing project-summary, embedding, and retrieval pipeline. However, that capability is outside the scope of the present project, which currently focuses on inspection, persistence, retrieval, and evidence-grounded inference over externally provided models.

## References

- [Chat Service](chat-service.md) - How embeddings integrate with chat flow
- [Tool Calling Conventions](tool-calling-conventions.md) - How tools generate embeddable content
- [Tool Calling Architecture](tool-calling-architecture.md) - How tools interact with introspection
