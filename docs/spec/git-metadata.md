# Show the git log for `{path}`. / What is the git status of `{path}`?

## Purpose

Retrieve git metadata for a file or directory within a known git repository.
This covers commit history, current working-tree status, and blame information.
Git metadata is used by the agent to understand how recently files changed,
who made changes, and what work streams are active or stale.

This spec covers three related prompts:

- **Git log** — recent commits affecting a path.
- **Git status** — working-tree changes relative to HEAD.
- **Git blame** — per-line attribution for a file.

---

## Git Log

### Inputs

| Parameter    | Type    | Required | Description                                                              |
| ------------ | ------- | -------- | ------------------------------------------------------------------------ |
| `path`       | string  | Yes      | File or directory to scope the log to. Resolved against cwd if relative. |
| `maxCommits` | integer | No       | Number of commits to return. Default: 20.                                |
| `oneline`    | boolean | No       | Return compact one-line format. Default: `true`.                         |

### Expected Output

```
Git log: /abs/path/to/target  (last 20 commits)

a1b2c3d  2026-04-21  Fix resolveBaseUrl NPE in OllamaChatModelProducer
e4f5a6b  2026-04-20  Add /analyze alias to SummaryResource switch
...
```

### Behaviour Rules

1. The path must reside within a git repository. If no `.git` ancestor exists,
   return: `"Error: path is not inside a git repository: <absolute-path>"`.
2. Commits are returned in reverse-chronological order (most recent first).
3. When `oneline` is `true`, each line is: `<short-hash>  <date>  <subject>`.
4. When `oneline` is `false`, each commit block includes hash, author, date,
   and full message body.

---

## Git Status

### Inputs

| Parameter | Type   | Required | Description                                                        |
| --------- | ------ | -------- | ------------------------------------------------------------------ |
| `path`    | string | Yes      | Repository root or subdirectory. Resolved against cwd if relative. |

### Expected Output

```
Git status: /abs/path/to/repo

Modified:
  src/main/java/ac/uk/sussex/kn253/tools/WorkspaceContextTools.java

Untracked:
  docs/spec/

Staged:
  (none)
```

### Behaviour Rules

1. Returns modified, staged, untracked, and deleted entries grouped by state.
2. Paths are reported relative to the repository root.
3. If the working tree is clean, return: `"Working tree clean."`.

---

## Git Blame

### Inputs

| Parameter   | Type    | Required | Description                                                        |
| ----------- | ------- | -------- | ------------------------------------------------------------------ |
| `path`      | string  | Yes      | Path to a tracked file.                                            |
| `startLine` | integer | No       | First line to attribute (1-based). Default: 1.                     |
| `endLine`   | integer | No       | Last line to attribute (1-based, inclusive). Default: end of file. |

### Expected Output

```
Git blame: /abs/path/to/file.java  [lines 1–20]

  1  a1b2c3d  2026-04-21  vscode   public class WorkspaceContextTools {
  2  a1b2c3d  2026-04-21  vscode       @Inject
...
```

### Behaviour Rules

1. Path must be a tracked file within a git repository.
2. Each line shows: line number, short commit hash, date, author, and line content.

---

## Edge Cases (all variants)

| Situation                             | Expected behaviour                              |
| ------------------------------------- | ----------------------------------------------- |
| Path is not inside any git repository | Return a descriptive error; no partial output.  |
| Repository has no commits             | Return: `"Repository has no commits yet."`.     |
| `maxCommits` is 0                     | Treat as default (20).                          |
| Detached HEAD state                   | Include a note: `"HEAD is detached at <hash>"`. |
