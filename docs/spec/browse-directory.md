# List the contents of `{path}`. If no path is given, list the current working directory.

## Purpose

Browse the immediate children of a directory, or recursively list all
descendant files and folders. This is the primary orientation tool: the agent
uses it before reading files, before summarising a folder, and whenever it
needs to understand what is present at a given location.

---

## Inputs

| Parameter    | Type    | Required | Description                                                                                                                                   |
| ------------ | ------- | -------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `path`       | string  | No       | Target directory. Resolved against the current working directory if relative. Defaults to the current working directory when blank or absent. |
| `recursive`  | boolean | No       | When `true`, walk all descendant directories. Default: `false`.                                                                               |
| `maxResults` | integer | No       | Maximum number of entries to return when `recursive` is `true`. Range: 1–1000. Default: 200.                                                  |

---

## Expected Output

### Non-recursive (default)

A structured list of immediate children. Each entry includes:

- Entry type: `[D]` for directory, `[F]` for file.
- Entry name (filename only, not full path).
- Entries sorted alphabetically, directories and files interleaved.

Example:

```
Directory: /home/user/projects/pdhd
[D] docs
[D] src
[F] pom.xml
[F] README.md
```

### Recursive

A flat list of file paths relative to the target directory, sorted
lexicographically by full relative path, truncated at `maxResults`.

Example:

```
Directory: /home/user/projects/pdhd
Files listed: 42 (limit 200)
docs/architecture.md
docs/overview.md
src/main/java/ac/uk/sussex/kn253/PdhdLauncher.java
...
```

---

## Behaviour Rules

1. The resolved target path must be an existing directory. If the path does
   not exist, return an error: `"Error listing directory: path does not exist: <absolute-path>"`.
2. If the path exists but is a file, return an error: `"Error listing directory: not a directory: <absolute-path>"`.
3. All returned paths must be absolute or consistently relative to the stated
   root — never ambiguous.
4. Symlinks are followed for existence checks but reported as the entry type
   they point to.
5. Hidden entries (names starting with `.`) are included unless the caller
   explicitly opts out.
6. This operation must not modify any filesystem state.

---

## Edge Cases

| Situation                                                 | Expected behaviour                                                               |
| --------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Empty directory                                           | Return the directory header with zero entries and no error.                      |
| `maxResults` exceeded                                     | Return the first `maxResults` entries and append a note: `"(truncated at <n>)"`. |
| Permission denied on a subdirectory during recursive walk | Skip that subtree and continue; append a warning line noting the skipped path.   |
| Blank `path` argument                                     | Treat as current working directory.                                              |
