# Archive `{path}` into `{destination}`.

## Purpose

Compress a file or directory tree into a portable archive for backup, transfer,
or long-term storage. The agent uses this capability when a user wants to
snapshot a project folder, store a versioned copy of a document tree, or
reduce disk usage for an inactive workspace.

---

## Inputs

| Parameter       | Type    | Required | Description                                                                                                            |
| --------------- | ------- | -------- | ---------------------------------------------------------------------------------------------------------------------- |
| `path`          | string  | Yes      | Source file or directory to archive. Resolved against cwd if relative.                                                 |
| `destination`   | string  | Yes      | Output archive path, including extension (`.zip`, `.tar.gz`, `.tar.bz2`, `.tar.xz`). Resolved against cwd if relative. |
| `format`        | string  | No       | Archive format override: `zip`, `tar.gz`, `tar.bz2`, `tar.xz`. If absent, inferred from the `destination` extension.   |
| `includeHidden` | boolean | No       | Include hidden entries (names starting with `.`). Default: `true`.                                                     |
| `overwrite`     | boolean | No       | Overwrite `destination` if it already exists. Default: `false`.                                                        |

---

## Expected Output

A confirmation message with the absolute paths of the source and the produced
archive, plus entry and size statistics:

```
Archived: /abs/source/path  →  /abs/destination/project.tar.gz
Entries: 312 files, 8 directories
Compressed size: 4.2 MB
```

---

## Behaviour Rules

1. `path` must exist. If it does not, return:
   `"Error archiving: source does not exist: <absolute-path>"`.
2. When `overwrite` is `false` and `destination` exists, return:
   `"Error archiving: destination already exists: <absolute-destination>"`.
3. The archive format is determined by the `format` parameter if supplied;
   otherwise it is inferred from the `destination` extension. If neither
   resolves to a supported format, return:
   `"Error archiving: unsupported format"`.
4. Parent directories of `destination` are created automatically if they do not
   exist.
5. The destination archive must never be placed **inside** the source directory
   being archived; doing so would create a recursive archive. If the
   destination resolves to a child of the source, reject the operation.
6. Temporary files used during compression must be written outside the source
   tree and cleaned up on both success and failure.
7. Archiving a single file is valid; the archive contains that one entry.

---

## Edge Cases

| Situation                          | Expected behaviour                                                                                                               |
| ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Source is an empty directory       | Produce a valid archive containing zero entries (or just the empty directory entry).                                             |
| `includeHidden = false`            | Exclude all entries whose filename component starts with `.`.                                                                    |
| Disk full during compression       | Delete the partial archive and return a descriptive I/O error.                                                                   |
| Source directory contains symlinks | Archive the symlink itself, not the target (unless format does not support symlinks, in which case follow the link and note it). |
| Unsupported format string supplied | Return: `"Error archiving: unsupported format: <value>"`.                                                                        |
