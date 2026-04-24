# Write the following content to `{path}`: `{content}`

## Purpose

Create a new file or overwrite an existing file with the supplied text content.
This is the primary tool for persisting agent-generated output — plans, reports,
rewritten files, and structured notes — to the filesystem.

---

## Inputs

| Parameter       | Type    | Required | Description                                                                        |
| --------------- | ------- | -------- | ---------------------------------------------------------------------------------- |
| `path`          | string  | Yes      | Destination file path. Resolved against the current working directory if relative. |
| `content`       | string  | Yes      | The UTF-8 text to write.                                                           |
| `createParents` | boolean | No       | When `true`, create any missing parent directories. Default: `true`.               |
| `overwrite`     | boolean | No       | When `false`, refuse to write if the file already exists. Default: `true`.         |

---

## Expected Output

A confirmation message stating the absolute path written and the number of bytes
or characters written:

```
Written: /abs/path/to/file.txt (2 048 chars)
```

---

## Behaviour Rules

1. The resolved destination path must not point to an existing directory.
2. When `overwrite` is `false` and the file already exists, return an error:
   `"Error writing file: file already exists: <absolute-path>"`.
3. When `createParents` is `true`, intermediate directories are created with
   default permissions before the file is written.
4. When `createParents` is `false` and the parent directory does not exist,
   return an error: `"Error writing file: parent directory does not exist: <absolute-path>"`.
5. Content is written as UTF-8. The trailing newline behaviour must match the
   content supplied — no implicit newline is appended or stripped.
6. The write must be atomic where possible (write to a temp file, then rename)
   so a partial failure does not corrupt an existing file.

---

## Edge Cases

| Situation                                                        | Expected behaviour                                                |
| ---------------------------------------------------------------- | ----------------------------------------------------------------- |
| `content` is empty string                                        | Write a zero-byte file; this is valid.                            |
| Path includes `..` traversal that escapes a known workspace root | Reject with: `"Error writing file: path escapes workspace root"`. |
| Disk full                                                        | Propagate a descriptive I/O error; no partial file left on disk.  |
| `overwrite = false` and file does not yet exist                  | Proceed normally.                                                 |
