# Analyse the path `{path}` and return its metadata.

## Purpose

Resolve and normalise a filesystem path and return rich metadata about it:
type, size, timestamps, permissions summary, and a compact content preview.
This is the go-to tool when the agent or a user needs to understand a path
before deciding what to do with it.

---

## Inputs

| Parameter | Type   | Required | Description                                                                    |
| --------- | ------ | -------- | ------------------------------------------------------------------------------ |
| `path`    | string | Yes      | Path to inspect. Resolved against cwd if relative. Defaults to cwd when blank. |

---

## Expected Output

A structured metadata report, rendered as readable text:

```
Path:        /abs/path/to/target
Type:        directory            # or: file, symlink
Exists:      true
Size:        4.1 MB               # for files; for directories: total size of immediate children
Modified:    2026-04-21T10:33:00Z
Created:     2026-03-01T08:00:00Z
Permissions: rwxr-xr-x
Owner:       vscode
Children:    14                   # directories only: immediate child count
Preview:
  <first 5 lines of file content, or first 5 child names for a directory>
```

---

## Behaviour Rules

1. The path is resolved to an absolute, normalised form before any inspection.
2. If the path does not exist, return:
   `"Error analysing path: path does not exist: <absolute-path>"`.
3. For symlinks, report the symlink itself as type `symlink` and include a
   `LinksTo` field with the resolved target path.
4. `Size` for a file is the exact byte count. For a directory it is the sum
   of immediate child file sizes (non-recursive) reported in human-readable
   units (B / KB / MB / GB).
5. `Preview` for a file shows at most 5 lines of UTF-8 text. Binary files
   show `(binary)` instead.
6. `Preview` for a directory shows the first 5 child names sorted
   alphabetically.
7. This operation must not modify any filesystem state.

---

## Edge Cases

| Situation                                        | Expected behaviour                                                             |
| ------------------------------------------------ | ------------------------------------------------------------------------------ |
| Path is blank                                    | Analyse the current working directory.                                         |
| Symlink target does not exist (dangling symlink) | Report type as `symlink (dangling)` with the unresolvable target path.         |
| File with no read permission                     | Report metadata available from `stat`; set `Preview` to `(permission denied)`. |
| Very deep path (`> 512` components)              | Normalise and proceed; no depth limit on path analysis.                        |
