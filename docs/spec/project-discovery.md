# Discover git projects under `{path}`.

## Purpose

Scan a directory tree to locate candidate project roots — directories that
contain a `.git` folder or have a GitHub remote — and register them as known
projects. This is the entry point for onboarding a new workspace into PDHD.

---

## Inputs

| Parameter           | Type    | Required | Description                                                                                           |
| ------------------- | ------- | -------- | ----------------------------------------------------------------------------------------------------- |
| `path`              | string  | Yes      | Root directory to search from. Resolved against cwd if relative.                                      |
| `maxDepth`          | integer | No       | Maximum directory depth to descend. Default: 5.                                                       |
| `includeGithubOnly` | boolean | No       | When `true`, return only repositories whose `origin` remote points to `github.com`. Default: `false`. |

---

## Expected Output

A list of discovered project entries. Each entry includes:

- `absolutePath` — absolute, normalised path to the project root.
- `name` — inferred project name (last path component or value from `name` field in manifest files).
- `hasGit` — `true` when a `.git` directory is present.
- `remoteUrl` — git remote URL, or `null` if not a git repository or no remote set.
- `registeredProjectId` — ID of the `ProjectFolder` row if this path is already registered, `null` otherwise.

---

## Behaviour Rules

1. The search root must exist and be a directory.
2. Discovery walks the tree up to `maxDepth` levels, skipping directories that
   are themselves a descendant of an already-discovered project root (no
   nested project registration).
3. A directory qualifies as a project root if it contains `.git/` or, when
   `includeGithubOnly` is `true`, the origin remote URL contains `github.com`.
4. Symbolic links are followed once; cycles are detected and skipped.
5. Hidden directories (names starting with `.`) other than `.git` are not
   descended into.
6. Common dependency cache directories (`node_modules`, `.gradle`, `.m2`,
   `target`, `build`, `dist`) are excluded from the walk automatically.
7. Already-registered projects are included in the result set but marked with
   their `registeredProjectId`; they are not re-registered automatically.
8. The tool does not modify any filesystem state; registration is a separate
   step driven by the user or a higher-level workflow.

---

## Edge Cases

| Situation                              | Expected behaviour                                                                 |
| -------------------------------------- | ---------------------------------------------------------------------------------- |
| No projects found                      | Return an empty list with a note; no error.                                        |
| Search root is itself a project root   | Include it in the results.                                                         |
| `maxDepth = 0`                         | Only inspect the root directory itself.                                            |
| Very large tree (> 10 000 directories) | Stop walk after scanning 10 000 directories and append a warning noting the limit. |
| Inaccessible subdirectory              | Skip and continue; log a warning line for the skipped path.                        |
