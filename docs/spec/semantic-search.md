# Find context related to `{query}` in the embedding store for project `{projectId}`.

## Purpose

Query the project's embedding store for chunks of text that are semantically
similar to the supplied query. The results are used to ground AI-generated
summaries, analysis, and next-step responses in real evidence stored from
previous inspections.

---

## Inputs

| Parameter    | Type    | Required | Description                                                                                        |
| ------------ | ------- | -------- | -------------------------------------------------------------------------------------------------- |
| `query`      | string  | Yes      | Natural-language query or keyword phrase.                                                          |
| `projectId`  | long    | Yes      | Registered project ID. Scopes the search to that project's embedded chunks.                        |
| `maxResults` | integer | No       | Maximum number of chunks to return. Range: 1–20. Default: 5.                                       |
| `minScore`   | float   | No       | Minimum cosine similarity score (0.0–1.0) for a chunk to be included. Default: 0.0 (no filtering). |

---

## Expected Output

An ordered list of the most relevant chunks (highest score first). Each chunk
entry includes:

- `score` — cosine similarity score (0.0–1.0).
- `sourcePath` — the file path the chunk was extracted from.
- `text` — the chunk text.

Example:

```
Embedding context for query: "entry point"
Project: 1  |  Results: 3 / 5 requested

[0.92]  src/main/java/ac/uk/sussex/kn253/PdhdLauncher.java
  PdhdLauncher is the root Picocli command.  It bootstraps the Quarkus runtime...

[0.87]  docs/architecture.md
  The entry point for the application is PdhdLauncher.main(String[] args)...

[0.74]  README.md
  Run with: java -jar pdhd.jar
```

---

## Behaviour Rules

1. `projectId` must reference an existing `ProjectFolder` with at least one
   stored embedding. If none exist, return an empty result set and a note:
   `"No embeddings found for project <id>. Run summarisation first."`.
2. The query is embedded using the configured embedding model before the
   similarity search.
3. Results are ordered by descending cosine similarity.
4. Chunks below `minScore` are excluded from the result set.
5. If the embedding model is unavailable, return an error rather than an
   empty result set, so the caller knows the query was not executed.
6. This operation is read-only; it must not add, modify, or delete any
   stored embeddings.
7. Chunk text is returned verbatim as stored; no re-summarisation is performed.

---

## Edge Cases

| Situation                                         | Expected behaviour                                                                                                                                                 |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `query` is blank                                  | Return: `"Error querying embeddings: query is blank"`.                                                                                                             |
| Fewer chunks exist than `maxResults`              | Return all available chunks; no error.                                                                                                                             |
| All chunks fall below `minScore`                  | Return an empty result set with a note indicating the score threshold.                                                                                             |
| Embedding model name mismatch (stored vs current) | Return the closest available results but append a warning: `"Warning: stored embeddings may use a different model than the currently configured embedding model"`. |
