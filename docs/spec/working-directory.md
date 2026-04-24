# What is the current working directory? / Change the working directory to `{path}`.

## Purpose

Get or change the agent's active working directory. The current working
directory (cwd) is the implicit base for all relative paths accepted by every
other tool. Keeping it accurate is critical to correct path resolution across
a session.

This spec covers two related prompts:

- **Get cwd** — return the current working directory as an absolute path.
- **Change cwd** — switch the working directory to a validated path within a
  known workspace root.

---

## Get Current Working Directory

### Expected Output

The absolute, normalised filesystem path of the current working directory:

```
/home/vscode/projects/pdhd
```

### Behaviour Rules

1. The returned path is always absolute and normalised (`toAbsolutePath().normalize()`).
2. The value must reflect the application-level cwd managed by `CwdService`, not
   the JVM process cwd (which may differ in a containerised environment).
3. The operation is read-only; it must not modify any state.

---

## Change Working Directory

### Inputs

| Parameter | Type   | Required | Description                                                          |
| --------- | ------ | -------- | -------------------------------------------------------------------- |
| `path`    | string | Yes      | Destination directory. Resolved against the current cwd if relative. |

### Expected Output

A confirmation message with the new absolute cwd:

```
Current working directory changed to: /home/vscode/projects/pdhd/src
```

### Behaviour Rules

1. The resolved destination must be an existing directory.
2. The destination must reside within a known workspace root registered with
   the application. Paths that escape all known roots are rejected with:
   `"Error changing working directory: path is outside all known workspace roots"`.
3. `..` traversal is permitted as long as the normalised result still falls
   within a known workspace root.
4. On success, `CwdService` persists the new cwd so it survives a page
   reload or session restart.
5. A `CwdResolvedEvent` is fired after the change so downstream subscribers
   (e.g. UI components) can react.

---

## Edge Cases

| Situation                      | Expected behaviour                                                                  |
| ------------------------------ | ----------------------------------------------------------------------------------- |
| Destination does not exist     | Return: `"Error changing working directory: path does not exist: <absolute-path>"`. |
| Destination is a file          | Return: `"Error changing working directory: not a directory: <absolute-path>"`.     |
| Destination is the current cwd | Accept and confirm; treat as a no-op.                                               |
| No registered workspace roots  | Any existing absolute directory path is accepted.                                   |
