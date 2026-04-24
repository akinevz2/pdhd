# Cache what we know about project `{projectId}`: `{note}` / Recall everything known about project `{projectId}`.

## Purpose

Persist a structured knowledge note about a project so it can be retrieved in
future sessions without re-inspecting the filesystem. The dual capability —
recall — queries those stored notes and returns them as grounded context for
further analysis.

This spec covers two related prompts:

- **Cache** — append or update a knowledge note for a project.
- **Recall** — return all stored knowledge for a project.

---

## Cache Project Knowledge

### Inputs

| Parameter   | Type   | Required | Description                                                                   |
| ----------- | ------ | -------- | ----------------------------------------------------------------------------- |
| `projectId` | long   | Yes      | Registered project ID.                                                        |
| `note`      | string | Yes      | The knowledge text to persist. Free-form markdown.                            |
| `tag`       | string | No       | Optional tag for categorising the note (e.g. `purpose`, `structure`, `risk`). |

### Expected Output

```
Knowledge cached for project <projectId>  [tag: <tag>]
Note length: 342 chars
```

### Behaviour Rules

1. `projectId` must reference an existing `ProjectFolder` row. If not found,
   return: `"Error caching knowledge: project not found: <id>"`.
2. Notes are appended, not replaced. A project accumulates multiple notes over
   time.
3. Each note is stored with a UTC timestamp so notes can be ordered
   chronologically.
4. Empty `note` strings are rejected: `"Error caching knowledge: note is blank"`.

---

## Recall Project Knowledge

### Inputs

| Parameter   | Type    | Required | Description                                                         |
| ----------- | ------- | -------- | ------------------------------------------------------------------- |
| `projectId` | long    | Yes      | Registered project ID.                                              |
| `tag`       | string  | No       | Filter returned notes to those matching this tag.                   |
| `maxNotes`  | integer | No       | Maximum number of notes to return (most recent first). Default: 20. |

### Expected Output

All stored notes for the project, most recent first:

```
Project knowledge for <projectId>  (<n> notes)
---
[2026-04-21T10:00:00Z]  [tag: purpose]
This project is a local filesystem inspection tool...
---
[2026-04-20T14:22:00Z]  [tag: structure]
The main entry point is PdhdLauncher...
```

### Behaviour Rules

1. If no notes exist, return an empty result set with a note; no error.
2. Notes are returned in descending timestamp order (most recent first).
3. When `tag` is supplied, only notes matching that tag are returned.
4. Recall is read-only; it must not modify any stored notes.

---

## Edge Cases

| Situation                               | Expected behaviour                                                                    |
| --------------------------------------- | ------------------------------------------------------------------------------------- |
| `projectId` not found on recall         | Return: `"Error recalling knowledge: project not found: <id>"`.                       |
| `tag` filter matches no notes           | Return empty result set; no error.                                                    |
| Note text is very long (> 10 000 chars) | Accept and store; truncate only at display time if `maxChars` is set by the UI layer. |
