# Summarise the folder at `{path}` and return a markdown summary.

## Purpose

Generate an AI-backed, evidence-grounded markdown summary for a folder or
individual file. Summaries are the primary way PDHD builds a reusable,
persisted understanding of a project. The output is structured markdown that
can be recalled later without re-reading the source files.

---

## Inputs

| Parameter         | Type    | Required | Description                                                                |
| ----------------- | ------- | -------- | -------------------------------------------------------------------------- |
| `path`            | string  | Yes      | Directory or file to summarise. Resolved against cwd if relative.          |
| `projectId`       | long    | Yes      | ID of the registered project this path belongs to.                         |
| `entryUuid`       | string  | Yes      | UUID that identifies this path within the project's file tree.             |
| `persist`         | boolean | No       | Whether to store the summary in the database. Default: `true`.             |
| `maxFiles`        | integer | No       | Maximum number of files sampled from the folder. Range: 1–32. Default: 32. |
| `maxCharsPerFile` | integer | No       | Maximum characters read from each sampled file. Default: 24 000.           |

---

## Expected Output

A `FolderSummaryResponse` containing:

- `folderPath` — normalised relative path within the project root.
- `summary` — rendered markdown string.
- `analysedFiles` — number of files that contributed content to the summary.
- `skippedFiles` — number of files excluded (binary, too large, unreadable).
- `updatedAt` — ISO-8601 timestamp of the stored summary row, or `null` if not persisted.
- `fallbackReason` — non-null if the primary model was unavailable and a fallback was used.
- `persisted` — `true` when the summary was stored to the database.

---

## Behaviour Rules

1. The target path must be an existing, accessible directory. A non-existent
   or non-directory path returns HTTP 404.
2. Files are selected by walking the directory up to `maxFiles`, sorted
   lexicographically so selection is deterministic across runs.
3. Binary files and files that exceed `maxCharsPerFile` characters are skipped;
   they count against `skippedFiles`.
4. Blank files are skipped silently.
5. The prompt sent to the model is RAFT-style: it includes sampled file
   contents as explicit context and asks the model to ground its summary in
   that evidence.
6. If the primary Ollama endpoint is unreachable, the system falls back to the
   configured fallback endpoint. The `fallbackReason` field records the reason.
7. When `persist` is `true`, the summary is stored as a `StructuredSummary` row
   with `SummaryType.FOLDER` and the normalised folder path as `targetPath`.
   A pre-existing row for the same `(project, type, targetPath)` is updated, not
   duplicated.
8. The `/api/summary/analyze` route is an alias for `/api/summary/folder` and
   must behave identically.

---

## Edge Cases

| Situation                                      | Expected behaviour                                                                  |
| ---------------------------------------------- | ----------------------------------------------------------------------------------- |
| Folder contains only binary files              | Return a summary noting zero analysed files; `skippedFiles` equals the total.       |
| `maxFiles` reached before all files are walked | Include only the first `maxFiles` entries (sorted); note the limit in the response. |
| Model returns an empty response                | Store an empty summary string; do not retry automatically.                          |
| Folder has already been summarised             | Overwrite the existing `StructuredSummary` row and update `updatedAt`.              |
| `persist = false`                              | Return the summary response without writing to the database.                        |
