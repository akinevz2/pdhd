# Embeddings Integration Guide

## Overview

This document describes the embedding functionality for PDHD, which enhances the assistant's understanding and retrieval capabilities by converting user input and project content into semantic vector representations. Embeddings enable semantic search, content retrieval, and context enrichment throughout the conversation flow.

## Architecture

### Embedding Generation Pipeline

```
User Input
    ↓
ChatService.sendMessage()
    ↓
[NEW] EmbeddingService.generateEmbedding(userInput)
    ├─ Query Ollama embedding model
    └─ Store vector in EmbeddingStore
    ↓
OllamaChatSession.send()
    ├─ [Enhanced] SystemPromptBuilder includes relevant embedded content
    ├─ Tool execution (tools can access embeddings)
    └─ Response generation
    ↓
[NEW] Response embedding stored for future retrieval
```

### Core Components

#### EmbeddingService

**Location:** `src/main/java/ac/uk/sussex/kn253/services/EmbeddingService.java` (to be created)

**Scope:** Application-scoped singleton

**Responsibilities:**

- Generate embeddings for text via Ollama API
- Manage embedding storage and retrieval
- Handle embedding failures gracefully
- Coordinate with introspection tools

**Key Methods:**

```java
public class EmbeddingService {

    /**
     * Generate embedding for input text.
     * Returns null if embedding generation fails.
     */
    public EmbeddingVector generateEmbedding(String text, String memoryId);

    /**
     * Retrieve semantically similar content.
     * Returns up to limit results, sorted by similarity.
     */
    public List<EmbeddingMatch> search(String query, int limit, String memoryId);

    /**
     * Store embedding with metadata for later retrieval.
     */
    public void storeEmbedding(
        EmbeddingVector vector,
        String sourceId,
        String contentSnippet,
        EmbeddingMetadata metadata
    );

    /**
     * Retrieve recent embeddings for a conversation.
     */
    public List<EmbeddingMatch> getRecentEmbeddings(String memoryId, int limit);
}
```

#### EmbeddingVector

**Purpose:** Represents a single embedding vector

```java
public class EmbeddingVector {
    private final String id;           // Unique identifier
    private final float[] vector;      // Vector representation (e.g., 384-dim)
    private final String text;         // Original text
    private final long timestamp;      // When embedding was created
    private final String sourceType;   // "user_input", "file_content", "tool_output"
}
```

#### EmbeddingStore

**Location:** Database table `embeddings`

**Schema:**

```sql
CREATE TABLE embeddings (
    id                  VARCHAR(255) PRIMARY KEY,
    session_id          VARCHAR(255) NOT NULL,
    vector              BLOB NOT NULL,         -- Vector as binary
    text_snippet        TEXT,                  -- Original text (truncated)
    full_text           TEXT,                  -- Full text (optional)
    source_type         VARCHAR(50),           -- user_input, file_content, tool_output
    source_id           VARCHAR(255),          -- Reference to source
    timestamp           BIGINT NOT NULL,
    similarity          FLOAT DEFAULT 0,       -- Populated during search
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE INDEX idx_embeddings_session_timestamp
    ON embeddings(session_id, timestamp DESC);
```

### Configuration

Embedding settings are stored in `OllamaSettings`:

```java
public class OllamaSettings {
    private String embeddingModel;           // e.g., "qwen3-embedding"
    private String embeddingBaseUrl;         // e.g., "http://desktop-box26:11434"
    private int embeddingDimension;          // e.g., 384
    private boolean embeddingEnabled;        // Enable/disable embeddings
    private int embeddingMaxResults;         // Default search limit
}
```

Default configuration for testing:

```properties
# application.properties
quarkus.langchain4j.ollama.embedding-model-name=qwen3-embedding
quarkus.langchain4j.ollama.embedding-base-url=http://desktop-box26:11434
quarkus.langchain4j.ollama.embedding-dimension=384
quarkus.langchain4j.ollama.embedding-enabled=true
```

## Integration Points

### 1. Chat Service Integration

When `ChatService.sendMessage()` is called:

```java
public String sendMessage(final String message) {
    final String directReply = directReply(message);
    if (directReply != null) {
        return directReply;
    }

    // [NEW] Generate embedding for user input
    if (embeddingService.isEnabled()) {
        EmbeddingVector userEmbedding = embeddingService.generateEmbedding(
            message,
            memoryId
        );
        if (userEmbedding != null) {
            embeddingService.storeEmbedding(
                userEmbedding,
                "user_input",
                message,
                new EmbeddingMetadata(memoryId, "user_input")
            );
        }
    }

    return chatSession.send(message);
}
```

**Effect:** Every user message is automatically embedded and stored for semantic retrieval.

### 2. System Prompt Builder Enhancement

The `SystemPromptBuilder` can include embedding-based context:

```java
public class SystemPromptBuilder {

    public String buildPrompt(
        String basePrompt,
        String userMessage,
        CurrentFolderMetadata metadata,
        EmbeddingService embeddingService  // [NEW]
    ) {
        StringBuilder prompt = new StringBuilder(basePrompt);

        // [NEW] Add relevant embedded content to context
        if (embeddingService.isEnabled()) {
            List<EmbeddingMatch> related = embeddingService.search(
                userMessage,
                3,           // Top 3 similar items
                memoryId
            );

            if (!related.isEmpty()) {
                prompt.append("\n\n## Related Context (from semantic search):\n");
                for (EmbeddingMatch match : related) {
                    prompt.append(String.format(
                        "- [%.2f%% match] %s\n",
                        match.similarity() * 100,
                        match.text()
                    ));
                }
            }
        }

        return prompt.toString();
    }
}
```

**Effect:** Assistant has access to semantically similar prior content without explicit tool calls.

### 3. Introspection Tool Enhancement

New introspection tools leverage embeddings:

#### get_embedding_context

Retrieves recently embedded content related to the query:

```
Tool: get_embedding_context
Arguments:
  - query (required): The query to search for
  - limit (optional): Max results (default: 5)

Returns:
  JSON array of recent embeddings with similarity scores
```

**Example Result:**

```json
[
  {
    "id": "emb_123",
    "similarity": 0.87,
    "text": "File contains database schema ...",
    "sourceType": "file_content",
    "sourceId": "src/db/schema.sql",
    "timestamp": 1706500800000
  },
  {
    "id": "emb_124",
    "similarity": 0.82,
    "text": "User asked about database integration...",
    "sourceType": "user_input",
    "timestamp": 1706500600000
  }
]
```

#### get_recent_embeddings

Retrieves recent embeddings without query-based search:

```
Tool: get_recent_embeddings
Arguments:
  - limit (optional): Max results (default: 10)

Returns:
  JSON array of most recent embeddings in reverse chronological order
```

### 4. Read Tool Caching with Embeddings

When `ReadToolSupport` reads file contents:

```java
public class ReadToolSupport {

    @Override
    public String execute(ToolArguments args, String memoryId) {
        String filePath = args.getString("filePath");
        String contents = readFile(filePath);

        // [NEW] Embed file contents for semantic retrieval
        if (embeddingService.isEnabled()) {
            String[] chunks = chunkContent(contents, 512);  // Chunk large files
            for (String chunk : chunks) {
                EmbeddingVector embedding = embeddingService.generateEmbedding(
                    chunk,
                    memoryId
                );
                if (embedding != null) {
                    embeddingService.storeEmbedding(
                        embedding,
                        filePath,
                        chunk.substring(0, Math.min(100, chunk.length())),
                        new EmbeddingMetadata(memoryId, "file_content", filePath)
                    );
                }
            }
        }

        return contents;
    }
}
```

**Effect:** File contents are automatically made searchable via semantic queries.

### 5. Tool Output Embedding

Tool results can be embedded for context enrichment:

```java
public class ToolExecutionResultMessage {

    public void onResultGenerated(String toolName, String result, String memoryId) {
        // [NEW] Embed significant tool results
        if (shouldEmbed(toolName)) {
            EmbeddingVector embedding = embeddingService.generateEmbedding(
                result,
                memoryId
            );
            if (embedding != null) {
                embeddingService.storeEmbedding(
                    embedding,
                    toolName,
                    result,
                    new EmbeddingMetadata(memoryId, "tool_output", toolName)
                );
            }
        }
    }
}
```

**Configuration:** Which tool outputs to embed (in `EmbeddingService`):

```java
private static final Set<String> EMBEDDABLE_TOOLS = Set.of(
    "read_file",
    "read_folder_manifest",
    "read_project_manifest",
    "summarize_path",
    "analyze_path_detailed"
);
```

## Respec the Introspection Functionality

Embeddings must maintain respect for existing introspection capabilities:

### 1. Tool Activity Tracking

Embedding operations are NOT recorded as tool activities:

```java
// In EmbeddingService
private void generateEmbedding(String text) {
    // This operation does NOT trigger ToolActivityService.recordToolExecution()
    // It's a service-level operation, not a user-visible tool
}

// But using get_embedding_context IS a tool
// And is properly recorded in ToolActivityService
```

### 2. Session Context Integration

The `get_session_context` introspection tool remains unchanged:

```
Tool: get_session_context

Returns:
  - currentWorkingDirectory: Current CWD
  - recentToolActivity: Recent 12 tool calls + results
  - [NEW] recentEmbeddings: Recent 5 embeddings (if embeddings enabled)
```

**Updated Response:**

```json
{
  "currentWorkingDirectory": "/path/to/project",
  "recentToolActivity": [
    {
      "toolName": "read_file",
      "args": { "filePath": "src/Main.java" },
      "result": "File contents...",
      "timestamp": 1706500800000
    }
  ],
  "recentEmbeddings": [
    {
      "similarity": 0.92,
      "text": "User asked about database...",
      "sourceType": "user_input",
      "timestamp": 1706500700000
    }
  ]
}
```

### 3. Memory ID Scoping

All embeddings are scoped to a specific `memoryId` (conversation session):

```java
// Embeddings stored with memoryId
embeddingService.storeEmbedding(
    vector,
    sourceId,
    snippet,
    new EmbeddingMetadata(memoryId, sourceType)
);

// Searches are scoped to current memoryId
List<EmbeddingMatch> results = embeddingService.search(
    query,
    limit,
    memoryId  // ← Only search this session
);
```

**Design Rationale:** Maintains isolation between conversations and respects session boundaries.

### 4. ProjectKnowledge Integration

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

### Embedding Status

```bash
GET /api/embeddings/status

Response:
{
  "enabled": true,
  "model": "qwen3-embedding",
  "baseUrl": "http://desktop-box26:11434",
  "dimension": 384
}
```

### Search Recent Embeddings

```bash
POST /api/embeddings/search
Content-Type: application/json

{
  "query": "database schema",
  "limit": 5
}

Response:
[
  {
    "id": "emb_123",
    "similarity": 0.87,
    "text": "File contains database schema...",
    "sourceType": "file_content",
    "sourceId": "src/db/schema.sql",
    "timestamp": 1706500800000
  },
  ...
]
```

### Get Recent Embeddings

```bash
GET /api/embeddings/recent?limit=10

Response:
[
  {
    "id": "emb_125",
    "sourceType": "user_input",
    "text": "How should I structure the database?",
    "timestamp": 1706500900000
  },
  ...
]
```

### Clear Session Embeddings

```bash
DELETE /api/embeddings/session/{memoryId}

Response:
{
  "deletedCount": 42
}
```

## Error Handling and Fallbacks

### Embedding Failures

If embedding generation fails, the system gracefully degrades:

```java
public String sendMessage(final String message) {
    if (embeddingService.isEnabled()) {
        try {
            EmbeddingVector embedding = embeddingService.generateEmbedding(
                message,
                memoryId
            );
            embeddingService.storeEmbedding(embedding, ...);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to generate embedding for user message");
            // Continue with normal chat flow - no embedding enhancement
        }
    }

    return chatSession.send(message);
}
```

**Implications:**

- Embedding failures do NOT block conversation
- Chat continues with or without embedding context
- Failures are logged for debugging
- Graceful degradation to non-embedding mode

### Vector Dimension Mismatch

If embedding dimensions change (e.g., switching models):

```java
public List<EmbeddingMatch> search(String query, int limit, String memoryId) {
    EmbeddingVector queryVector = generateEmbedding(query, memoryId);

    if (queryVector.dimension() != storedDimension()) {
        LOG.warnf(
            "Dimension mismatch: query %d vs stored %d",
            queryVector.dimension(),
            storedDimension()
        );
        return List.of();  // Return empty - cannot compare
    }

    // Proceed with search
}
```

### Service Unavailable

If embedding model service is unavailable:

```java
public EmbeddingVector generateEmbedding(String text, String memoryId) {
    try {
        // Call embedding model API
        return client.embed(text);
    } catch (TimeoutException | ConnectException e) {
        LOG.warnf("Embedding service unavailable: %s", e.getMessage());
        return null;  // Signal failure gracefully
    }
}
```

## Performance Considerations

### Embedding Generation Latency

- Typical latency per embedding: 100-500ms (varies by model and vector dimension)
- User message embedding adds ~200ms to response time
- File embeddings are generated asynchronously in background

### Control Embedding Generation

Embeddings can be generated asynchronously:

```java
@Asynchronous
public void generateEmbeddingAsync(String text, String memoryId) {
    // Non-blocking embedding generation for files
    EmbeddingVector embedding = generateEmbedding(text, memoryId);
    storeEmbedding(embedding, ...);
}
```

Usage:

```java
// For user input: synchronous (users expect it)
embeddingService.generateEmbedding(message, memoryId);

// For file content: asynchronous (background)
embeddingService.generateEmbeddingAsync(content, memoryId);
```

### Vector Search Performance

Similarity search uses cosine distance:

```
similarity(v1, v2) = (v1 · v2) / (|v1| * |v2|)
```

- Complexity: O(n) for exact search (n = embedding count)
- Index: Database index on session_id + timestamp for fast filtering
- Result: Typical search < 50ms for 1000 embeddings

### Vector Storage Optimization

Store vectors efficiently:

```
Vector Size = 384 floats × 4 bytes = 1.5 KB per embedding
Storage for 10,000 embeddings = 15 MB
Sufficient for typical project sessions
```

## Configuration and Tuning

### Disable Embeddings

To run without embeddings (e.g., embedding service unavailable):

```properties
quarkus.langchain4j.ollama.embedding-enabled=false
```

System automatically skips embedding generation and stores.

### Change Embedding Model

Switch to a different embedding model:

```properties
quarkus.langchain4j.ollama.embedding-model-name=nomic-embed-text
quarkus.langchain4j.ollama.embedding-base-url=http://desktop-box26:11434
quarkus.langchain4j.ollama.embedding-dimension=768
```

Clear old embeddings before switching (different dimensions):

```bash
DELETE /api/embeddings/clear-all
```

### Tune Chunk Sizes

For large file embeddings, chunk to avoid very long vectors:

```java
// In ReadToolSupport.java
private static final int CHUNK_SIZE = 512;  // Tokens per chunk
private static final int OVERLAP = 50;       // Overlap between chunks

private String[] chunkContent(String content, int chunkSize) {
    // Implement sliding window chunking
}
```

### Tuning Search Limits

Default embedding search results:

```java
private static final int DEFAULT_SEARCH_LIMIT = 5;
private static final int MAX_SEARCH_LIMIT = 20;

public List<EmbeddingMatch> search(String query, int limit, String memoryId) {
    int actualLimit = Math.min(limit, MAX_SEARCH_LIMIT);
    // ...
}
```

## Testing

### Unit Tests

```java
public class EmbeddingServiceTest {

    @Test
    public void testEmbeddingGeneration() {
        EmbeddingService service = new EmbeddingService(mockClient);
        EmbeddingVector vector = service.generateEmbedding("test text", "mem-1");

        assertThat(vector).isNotNull();
        assertThat(vector.dimension()).isEqualTo(384);
        assertThat(vector.similarity(vector)).isEqualTo(1.0f);
    }

    @Test
    public void testSemanticSearch() {
        EmbeddingService service = new EmbeddingService(mockClient);
        service.storeEmbedding(vec1, "src1", "database schema", meta1);
        service.storeEmbedding(vec2, "src2", "database integration", meta2);

        List<EmbeddingMatch> results = service.search("database", 5, "mem-1");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).similarity()).isGreaterThan(0.8f);
    }
}
```

### Integration Tests

```java
public class EmbeddingIntegrationTest {

    @Test
    public void testUserInputEmbeddingFlow() {
        String memoryId = "test-conv";
        String userMessage = "How do I query the database?";

        chatService.sendMessage(userMessage);

        // Verify embedding was generated and stored
        List<EmbeddingMatch> recentEmbeddings =
            embeddingService.getRecentEmbeddings(memoryId, 1);

        assertThat(recentEmbeddings).hasSize(1);
        assertThat(recentEmbeddings.get(0).text()).contains("query");
    }

    @Test
    public void testToolOutputEmbedding() {
        String filePath = "src/Main.java";
        String content = "public class Main {..}";

        readToolSupport.execute(
            ToolArguments.parse(Map.of("filePath", filePath)),
            "mem-1"
        );

        // Verify file content was embedded
        List<EmbeddingMatch> results =
            embeddingService.search("public class", 5, "mem-1");

        assertThat(results.stream()
            .anyMatch(m -> m.sourceId().equals(filePath)))
            .isTrue();
    }
}
```

## Debugging and Observability

### Check Embedding Status

```bash
curl http://localhost:8080/api/embeddings/status
```

### Search Embeddings via API

```bash
curl -X POST http://localhost:8080/api/embeddings/search \
  -H "Content-Type: application/json" \
  -d '{"query":"database schema","limit":5}'
```

### View Recent Embeddings

```bash
curl http://localhost:8080/api/embeddings/recent?limit=10
```

### Clear All Embeddings

```bash
curl -X DELETE http://localhost:8080/api/embeddings/clear-all
```

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

## References

- [Chat Service](chat-service.md) - How embeddings integrate with chat flow
- [Tool Calling Conventions](tool-calling-conventions.md) - How tools generate embeddable content
- [Tool Calling Architecture](tool-calling-architecture.md) - How tools interact with introspection
