## 6.2 Recommendations for Implementation

To strengthen both practical deployment and academic quality, implementation should proceed as a staged roadmap with measurable checkpoints.

### 1. Prioritize an evidence-first evaluation loop

Define a compact benchmark suite that reflects the actual workload of the system: file traversal, content retrieval, tool selection, and end-to-end response generation. Record baseline values before major changes.

At minimum, track:

- End-to-end request latency (P50, P95)
- Tool failure rate by tool name/category
- Cache hit rate for repeated exploration and read operations
- Success rate of multi-step user tasks completed without manual correction

This would convert architectural claims into repeatable evidence and improve confidence in reported outcomes.

### 2. Introduce typed contracts while preserving LLM-friendly interfaces

The current string-oriented tool interface is practical for conversational systems, but difficult to validate and compare over time. A hybrid contract is recommended:

- Keep a human-readable summary string for LLM consumption
- Add a typed payload for programmatic verification and analytics
- Version response schemas to support safe evolution

This reduces ambiguity in downstream integration while preserving usability for interactive prompts.

### 3. Strengthen dispatch safety and observability

As the number of tools grows, first-match module dispatch can create subtle regressions. To mitigate this, introduce:

- Startup-time duplicate-name checks across modules
- Explicit precedence rules documented in one place
- Per-tool telemetry for duration, error class, and argument validation failures

These controls make behavior more predictable and improve diagnosis of production issues.

### 4. Formalize cache freshness policy

Read-context caching improves iteration speed, but stale entries can degrade trust in the assistant output. A concrete cache policy should be specified:

- Time-to-live per content type
- Invalidation on file write or project refresh
- User-visible indication when cached data is returned

Clear freshness semantics improve both engineering reliability and transparency in the report's claims.

### 5. Improve reproducibility and experimental traceability

The report would benefit from a reproducible evaluation package that includes:

- Fixed test prompts and expected outcomes
- Environment specification (model version, hardware profile, runtime settings)
- A changelog linking design decisions to observed performance deltas

This supports stronger academic rigor and allows independent replication of results.

### 6. Calibrate scope with explicit risk management

Implementation planning should distinguish between immediate, medium-term, and exploratory work. A recommended sequencing model is:

- Phase A: reliability and correctness (dispatch checks, validation, error handling)
- Phase B: performance and scaling (latency optimization, cache policy tuning)
- Phase C: feature extension (new tools, richer orchestration, advanced routing)

Each phase should include entry/exit criteria so conclusions remain grounded in delivered capability rather than intent.

### 7. Add a concise practitioner recommendation summary

For clarity, include a one-page implementation checklist in the appendix that maps each recommendation to:

- Objective
- Owner/component
- Metric of success
- Validation method

This gives assessors a clear line from research findings to actionable engineering decisions.

### 8. Protect telemetry persistence as a hard operational constraint

Telemetry must be treated as durable historical evidence, not disposable runtime state.

- Never drop or truncate telemetry tables (currently `tool_telemetry`).
- Keep schema evolution additive/backward-compatible.
- Keep non-destructive ORM/migration behavior in all environments where telemetry history matters.
- Any automation, migration, or refactor that would remove telemetry history should be treated as a blocking change request and rejected until replaced with a safe migration path.

### 9. Standardize Support-class design for upcoming extension work

For maintainability, treat `*Support` classes as the canonical home for shared literals and policy constants that define backend behavior.

- Use `SchemaKeys` for payload keys only.
- Use `ToolSupport` for shared tool/API non-key values.
- Use `BackendSupport` for service/resource policy constants (host names, protocols, regex fragments, stable error text).

Implementation note for planned git-host expansion:

- Keep host-specific rules out of controllers.
- Add host policy constants in `BackendSupport`.
- Keep URL validation and selection in `RepoService`.
- Keep API payload contract stable (`repoUrl` or absent marker), so frontend logic remains simple.

This allows future support for GitLab/Bitbucket/self-hosted forge webpage links without scattering literals or changing frontend behavior.

### 10. Short Revision Summary (2026-03-30)

Today's integration work combined two major streams into the main line: a broad null-safety hardening effort and a frontend usability pass. On the backend side, nullability and error-handling behavior were tightened across API resources, service-layer resolution logic, model classes, and related tests. This included clearer exception boundaries for repository resolution, improved handling of non-git directories during discovery flows, and better consistency in how failures are surfaced to callers.

In parallel, the frontend browser and assistant interaction flows were refined to reduce ambiguity and improve operator feedback. Folder navigation behavior was expanded, file-browser interactions were made more explicit, and UI error messages were adjusted to carry meaningful backend context rather than generic failure text. Repository pop-out behavior was also narrowed so links are attached only when entries qualify as valid project/repository targets under the current policy.

The practical outcome is that the current master branch reflects both reliability-focused backend work and usability-focused frontend work in a single coherent baseline. Discovery and repository-link behavior are now more predictable, path-resolution semantics are clearer, and the supporting documentation has been updated to capture implementation guidance and extension strategy.
