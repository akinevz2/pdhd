## Implementation Checklist (Recommendations)

This checklist maps recommendation items to concrete implementation and validation.

| Objective                                                                      | Owner/Component                                                | Metric of Success                                                                              | Validation Method                                                                 |
| ------------------------------------------------------------------------------ | -------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| Dispatch safety: deterministic module precedence and duplicate-name prevention | `ToolService`                                                  | Startup fails fast on duplicate tool names; execution order stable across runs                 | Unit tests: duplicate-name rejection; code-level precedence list in `ToolService` |
| Per-tool observability: latency, error class, argument validation failures     | `ToolTelemetryService`, `ToolService`, `IntrospectToolSupport` | Telemetry captures `calls`, `failures`, `validationFailures`, `avg/p50/p95` latencies per tool | Unit tests plus `get_session_context` output includes telemetry summary           |
| Cache freshness policy with explicit metadata                                  | `ReadToolSupport`                                              | Cached entries include `type`, `cachedAt`, `ttlSeconds`                                        | Inspect entries via `read_project_knowledge`                                      |
| User-visible cache freshness state                                             | `IntrospectToolSupport` (`read_project_knowledge`)             | Returned cache entries/tags include `cacheStatus`, `ageSeconds`, `ttlSeconds`                  | Manual call to `read_project_knowledge` with and without `tag`                    |
| Invalidation on write operations                                               | `WriteToolSupport` and write tools                             | Writes invalidate cached read artifacts (`file:*`, `path:*`, `folder:*`) for project           | Run write tool, then check returned invalidation count and cache contents         |
| Typed contracts at API boundary while preserving existing outputs              | `ProjectApiResource` + API models                              | Versioned responses include `schemaVersion`, `generatedAt`, summary, typed items               | Tests for `/api/tool-activity/v2` and `/api/tool-telemetry`                       |

### Notes

- Typed contract extension is currently delivered for observability/caching metadata while preserving existing string-first tool outputs.
- Additional benchmark/reporting automation can now build on telemetry snapshots without changing tool call behavior.
- Existing `/api/tool-activity` remains unchanged for compatibility; new versioned contracts are additive.
