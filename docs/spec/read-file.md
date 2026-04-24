# Read the file at `{path}` and return its text content.

## Purpose

Return the text content of a single file so the agent or a user can inspect,
analyse, or act on it. This is the primary tool for understanding individual
files before summarising, rewriting, or referencing them.

---

## Inputs

| Parameter   | Type    | Required | Description                                                                                      |
| ----------- | ------- | -------- | ------------------------------------------------------------------------------------------------ |
| `path`      | string  | Yes      | Path to the target file. Resolved against the current working directory if relative.             |
| `startLine` | integer | No       | 1-based line number to begin reading from. Default: 1 (start of file).                           |
| `endLine`   | integer | No       | 1-based inclusive line number to stop reading at. Default: end of file.                          |
| `maxChars`  | integer | No       | Maximum characters to return. Default: 24 000. Excess content is truncated with a trailing note. |

---

## Expected Output

The raw UTF-8 text of the file, optionally bounded by `startLine`/`endLine`,
never exceeding `maxChars` characters. The response includes a header line
noting the resolved absolute path and the line range actually returned:

```
File: /abs/path/to/file.txt  [lines 1–120]
<file content>
```

If the file is truncated due to `maxChars`, append:

```
... (truncated after 24000 chars)
```

---

## Behaviour Rules

1. The resolved path must point to an existing regular file.
2. Binary files (detected by file-type heuristic or MIME type) must be refused
   with: `"Error reading file: binary or non-text file: <absolute-path>"`.
3. If `startLine` > total line count, return an empty body and note the actual
   line count.
4. If `endLine` < `startLine`, treat as `endLine = startLine`.
5. The operation must not modify any filesystem state.
6. File encoding is assumed UTF-8. If decoding fails, return:
   `"Error reading file: cannot decode as UTF-8: <absolute-path>"`.

---

## Edge Cases

| Situation                             | Expected behaviour                                                          |
| ------------------------------------- | --------------------------------------------------------------------------- |
| File is empty                         | Return an empty body with the path header; no error.                        |
| Path is a directory                   | Return error: `"Error reading file: path is a directory: <absolute-path>"`. |
| Path does not exist                   | Return error: `"Error reading file: file not found: <absolute-path>"`.      |
| `startLine` and `endLine` both absent | Return full file content up to `maxChars`.                                  |
| Very large file                       | Honour `maxChars` limit and append truncation note.                         |
