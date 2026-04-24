# Filesystem Explorer Agent — Capability Specification Overview

This folder contains the prompt-driven specification for every capability the
PDHD filesystem explorer agent is required to support.

Each document in this folder follows a common structure:

- **Title heading** — the exact prompt (or prompt template) a user or caller
  would issue to invoke that capability.
- **Body** — human-readable specification of how the agent must behave,
  including inputs, outputs, edge cases, and failure modes.

---

## Capability Index

| Spec file                                      | Summary                                                     |
| ---------------------------------------------- | ----------------------------------------------------------- |
| [browse-directory.md](browse-directory.md)     | List the immediate or recursive contents of a directory     |
| [read-file.md](read-file.md)                   | Read the text content of a single file                      |
| [write-file.md](write-file.md)                 | Create or overwrite a file with supplied content            |
| [move-rename.md](move-rename.md)               | Move or rename a file or directory                          |
| [archive.md](archive.md)                       | Compress a directory or file set into an archive            |
| [summarise.md](summarise.md)                   | Generate an AI-backed markdown summary for a folder or file |
| [project-discovery.md](project-discovery.md)   | Discover git / GitHub project roots under a search path     |
| [path-analysis.md](path-analysis.md)           | Resolve, normalise, and report metadata for a path          |
| [working-directory.md](working-directory.md)   | Get or change the active working directory                  |
| [project-knowledge.md](project-knowledge.md)   | Persist or recall structured knowledge about a project      |
| [create-plan-report.md](create-plan-report.md) | Create a plan, report, or timeline markdown document        |
| [semantic-search.md](semantic-search.md)       | Query the embedding store for semantically relevant context |
| [git-metadata.md](git-metadata.md)             | Retrieve git log, status, or commit metadata for a path     |
| [web-search.md](web-search.md)                 | Search the web and return summarised results                |

---

## Design Principles

1. **Prompt as contract** — the title heading of every spec is a first-class
   prompt the system must be able to satisfy. If the system cannot satisfy a
   prompt, the gap is a defect, not an omission.

2. **Fail loudly at boundaries** — the agent must surface a clear, actionable
   error message for every failure mode listed in each spec. Silent fallbacks
   are not acceptable.

3. **Absolute paths everywhere** — all path-handling capabilities must
   resolve, normalise, and return absolute paths. Relative paths are an input
   convenience only; internal state and returned values are always absolute.

4. **Idempotent reads** — read, browse, summarise, and search operations must
   not mutate filesystem state.

5. **Minimal footprint writes** — write, move, archive, and knowledge-cache
   operations must write only to the location explicitly supplied by the caller.
   Implicit side-writes (temp files, intermediate directories created outside
   the target path) are prohibited unless documented in the spec.
