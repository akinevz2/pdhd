# Move `{source}` to `{destination}`.

## Purpose

Move or rename a file or directory from one location to another within the
filesystem. This covers both simple renames within the same parent directory
and full cross-directory moves.

---

## Inputs

| Parameter       | Type    | Required | Description                                                                                                                                    |
| --------------- | ------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `source`        | string  | Yes      | Current path of the file or directory. Resolved against cwd if relative.                                                                       |
| `destination`   | string  | Yes      | Target path. May be a new filename in the same directory (rename) or a path in a different directory (move). Resolved against cwd if relative. |
| `createParents` | boolean | No       | When `true`, create any missing parent directories in `destination`. Default: `true`.                                                          |
| `overwrite`     | boolean | No       | When `false`, refuse if `destination` already exists. Default: `false`.                                                                        |

---

## Expected Output

A confirmation message with the resolved absolute source and destination paths:

```
Moved: /abs/source/path  →  /abs/destination/path
```

---

## Behaviour Rules

1. Both `source` and `destination` are resolved to absolute, normalised paths
   before any operation is attempted.
2. `source` must exist. If it does not, return:
   `"Error moving: source does not exist: <absolute-source>"`.
3. When `overwrite` is `false` and `destination` already exists, return:
   `"Error moving: destination already exists: <absolute-destination>"`.
4. When `overwrite` is `true` and `destination` is an existing file, it is
   replaced atomically.
5. When `destination` is an existing **directory** and `overwrite` is `true`,
   the source is moved **into** that directory (preserving its name), not
   replacing the directory itself.
6. Cross-device moves (source and destination on different mount points) are
   handled by copy-then-delete. The copy is completed and verified before the
   source is removed; if the copy fails, the source is left untouched.
7. Moving a directory moves its entire subtree.
8. Path traversal that would cause `destination` to escape a known workspace
   root must be rejected.

---

## Edge Cases

| Situation                                          | Expected behaviour                                                       |
| -------------------------------------------------- | ------------------------------------------------------------------------ |
| Source and destination are the same resolved path  | No-op; return success.                                                   |
| Moving a directory into one of its own descendants | Reject: `"Error moving: destination is inside source"`.                  |
| Missing parent directory in destination            | Create parents if `createParents = true`; otherwise error.               |
| Permission denied                                  | Return a descriptive error; leave both source and destination unchanged. |
