# Cache Freshness Policy

**Component owner:** backend services  
**Effective date:** 2026-04-14  
**Related recommendation:** §4 Formalize cache freshness policy

---

## Scope

This policy covers all read-context data that the backend caches to avoid
repeated filesystem or AI-model calls within a single session. It does **not**
cover the persistent telemetry tables (`tool_telemetry`,
`model_call_telemetry`), which are governed by the retention rules in §8 of
the recommendations.

---

## Content types and TTL

| Content type                                               | Cache location             | TTL                              | Invalidation trigger                                       |
| ---------------------------------------------------------- | -------------------------- | -------------------------------- | ---------------------------------------------------------- |
| Structured file summary (`StructuredSummary`)              | `structured_summary` table | Indefinite until content changes | File write detected (content-hash mismatch on next upsert) |
| Project folder listing (`ProjectFolder`)                   | `project_folder` table     | Session-lived                    | Explicit project close / `DELETE /api/project/{id}`        |
| GitHub metadata (`GithubMetadata`)                         | `github_metadata` table    | Indefinite until re-enriched     | Manual refresh or project reload                           |
| In-memory directory listing (`listDirectoryContents` tool) | None – always live         | N/A                              | No caching; each call hits the filesystem                  |
| In-memory file read (`readFile` tool)                      | None – always live         | N/A                              | No caching; each call hits the filesystem                  |
| Web-search results (`searchWeb` tool)                      | None – always live         | N/A                              | No caching; each call is a fresh HTTP request              |

---

## Staleness risk

**Structured summaries** are the only data with a meaningful staleness window.
A summary can become stale if a file is edited outside the PDHD session. The
`StructuredSummaryStoreService#upsert` path computes a content-hash on every
call and only updates the stored record when the hash changes. If the LLM
consumer calls `readFile` directly rather than using the summary cache, it
always receives fresh data.

**Recommended guard:** before returning a cached summary as context, verify
that its `contentHash` matches the current file digest. This can be added as
an opt-in check in `FileSummarisationPipelineService`.

---

## User-visible cache indicator

When a response is derived from a cached structured summary, the assistant
should include a brief note such as:

> _Note: this summary was generated from a cached analysis. Refresh the
> project if the file has changed._

This is not yet automated; it is recorded here as a medium-term deliverable
(Phase 3 of the execution plan).

---

## Schema version tracking

`StructuredSummary` entities carry a `SCHEMA_VERSION` constant
(`StructuredSummaryStoreService.SCHEMA_VERSION = 1`). When the payload schema
changes, increment this constant and provide a migration path for existing rows.

---

## Decision log

| Date       | Decision                                           | Rationale                                                                                                                         |
| ---------- | -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| 2026-04-14 | No in-memory TTL for tool reads                    | Tool reads are lightweight filesystem calls; adding a TTL layer would add complexity without measurable benefit at current scale. |
| 2026-04-14 | Content-hash invalidation for structured summaries | Avoids clock-skew problems and makes staleness detection deterministic.                                                           |
