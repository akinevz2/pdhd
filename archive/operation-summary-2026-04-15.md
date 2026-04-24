# Operation Summary (2026-04-14)

This summary captures the implementation work completed while stepping through
`docs/recommendations.md`.

## Completed recommendations

### Recommendation 9 – Support-class standardisation

Implemented three new support classes:

- `ac.uk.sussex.kn253.support.BackendSupport`
- `ac.uk.sussex.kn253.support.ToolSupport`
- `ac.uk.sussex.kn253.support.SchemaKeys`

Refactored existing classes to consume these constants:

- `Origin`
- `WebSearchTools`
- `WorkspaceContextTools`
- `ReadFileTools`
- `GithubMetadataService`

Outcome: host/policy and module literals are now centralised for safer future
extension work.

### Recommendation 3 – Dispatch safety and observability

Added startup-time duplicate-name validation in
`ProjectAssistantProducer#validateToolNameUniqueness`.

Behavior:

- Scans all registered tool beans for `@Tool` methods.
- Derives each public tool name (annotation `name` or method name fallback).
- Throws `IllegalStateException` when duplicates are found.
- Logs a startup confirmation message when checks pass.

Outcome: ambiguous first-match dispatch regressions are now caught at startup.

Maintainer documentation added:

- `docs/tool-dispatch-precedence.md` defines the centralized dispatch
  precedence and naming policy, plus update steps when adding tool modules.

### Recommendation 8 – Telemetry persistence protection

Added startup guard in `TelemetryService#guardTelemetryPersistence`:

- Logs an error if Hibernate schema strategy is not `update`.
- Logs telemetry row counts for both `tool_telemetry` and
  `model_call_telemetry` at startup.

Also strengthened telemetry retention guidance in `ToolTelemetryRecord` Javadoc.

Outcome: destructive schema configuration becomes visible immediately in logs.

### Recommendation 2 – Typed contracts + LLM-friendly string interface

Extended telemetry storage for hybrid contracts:

- Added `typed_output_payload` (TEXT) to `ToolTelemetryRecord`.
- Added `output_schema_version` (INTEGER) to `ToolTelemetryRecord`.
- Added overloaded `TelemetryService#recordToolUse(...)` that accepts typed
  payload JSON and a schema version.

Outcome: tools can keep human-readable output while emitting typed analytics
payloads with explicit versioning.

### Recommendation 1 – Evidence-first evaluation loop

Created baseline benchmark assets:

- `docs/evaluation/benchmark-scenarios.md` (8 canonical scenarios)
- `scripts/benchmark.sh` (automated scenario runner producing JSON artifacts)

Metrics targeted:

- End-to-end latency (P50/P95 by post-processing run files)
- Tool failure / validation failure rates
- Multi-step task success rate
- Security-boundary pass rate

Outcome: practical foundation for repeatable baseline capture.

### Recommendation 4 – Cache freshness policy

Created `docs/cache-policy.md` defining:

- Content-type coverage
- TTL/invalidation model
- Staleness risks and mitigation
- User-visible cache indication guidance

Outcome: freshness semantics are explicit for implementation and reporting.

### Recommendation 5 – Reproducibility and traceability

Created reproducibility package docs:

- `docs/evaluation/environment-spec.md`
- `docs/evaluation/decision-log.md`

Outcome: benchmark runs and architecture decisions can now be traced with
environment context.

### Recommendation 7 – Practitioner one-page summary

Created `docs/implementation-checklist.md` with:

- Objective
- Owner/component
- Metric of success
- Validation method
- Status

Outcome: clear mapping from recommendations to concrete engineering actions.

## Remaining recommendations

- Recommendation 6 (phase sequencing and risk management) already existed in
  the source recommendations doc as §11 (Execution Plan Addendum).

## Notes

- The benchmark script was made executable (`chmod +x scripts/benchmark.sh`).
- No destructive migration behavior was introduced.
- Existing API payload contracts were preserved for current frontend paths.

## Benchmark execution evidence

- Benchmark command executed: `./scripts/benchmark.sh`
- Latest artifact before script fix: `docs/evaluation/results/run-2026-04-14T04:18:41.json`
- Observed issue: scenarios were incorrectly marked `success=true` despite
  backend `500 Internal Server Error` response content.

Corrective action applied:

- Updated `scripts/benchmark.sh` to include HTTP-status-aware pass/fail logic
  and server-error marker detection in response bodies.
- Added `http_status` to each scenario result row.
- Tightened S08 security scenario to fail if sensitive content appears.

Resulting status:

- Benchmark evidence generation is now stricter and better aligned with the
  acceptance criteria in `docs/evaluation/benchmark-scenarios.md`.

## Slow-workstation timeout adjustment

To support slower hardware, benchmark request timeout is now configurable and
uses a higher default.

- Script update: `scripts/benchmark.sh`
- New env var: `BENCHMARK_TIMEOUT_SECONDS`
- New default per-request timeout: `180s` (previously fixed at `60s`)

Validated run with extended timeout:

- Command: `BENCHMARK_TIMEOUT_SECONDS=240 ./scripts/benchmark.sh http://localhost:8083`
- Artifact: `docs/evaluation/results/run-2026-04-14T04:41:29.json`
- Outcome: `7/8` scenarios passed; `S08` failed with
  `SECURITY FAIL: response did not indicate access denied`.

This confirms timeout-related false negatives are removed while still allowing
the script to catch genuine scenario failures.

## Retry policy note

Implementation and benchmark runs currently follow a conservative retry policy:

- Avoid automatic retries by default.
- Prefer explicit timeout configuration to bound slow or unstable calls.
- When a call fails or times out, require manual user confirmation before
  retrying benchmark runs or model-dependent operations.

Rationale:

- Automatic retries can hide real stability issues, inflate apparent success
  rates, and make benchmark evidence harder to interpret.
- Manual confirmation keeps operator intent explicit and preserves trustworthy
  failure/success traces in evaluation artifacts.

Policy extension implemented:

- Frontend:
  - Retry actions now require explicit user confirmation before resubmitting
    failed API signals or assistant chat retries.
  - This is enforced in the UI retry handlers, not as an optional convention.
- Backend:
  - Failure paths in Ollama management operations now log an explicit
    no-automatic-retry policy note.
  - The service surfaces failures and leaves retry decisions to explicit,
    user-confirmed actions from the frontend/operator.

## Observability, logging, and transparency policy

Implementation and operations follow a cross-cutting observability policy:

- Prefer explicit, human-readable logs for startup checks, policy guards, and
  failure conditions that affect reliability decisions.
- Do not silently recover from critical control failures (for example,
  ambiguous tool registration or destructive telemetry schema settings).
- Record key behavior outcomes in durable telemetry where available
  (`tool_telemetry`, `model_call_telemetry`) so historical evidence can be
  audited.
- Ensure benchmark artifacts preserve failure signals (HTTP status, error
  markers) rather than masking them with optimistic success paths.
- Keep operator-facing retry behavior transparent: retries are user-confirmed,
  not hidden automatic loops.

Transparency rule for report interpretation:

- Distinguish true runtime failures from warmup/environment effects in
  benchmark analysis and document assumptions in evaluation artifacts.

## Frontend testing policy requirement

A formal frontend testing policy still needs to be implemented.

Required scope for the policy:

- Define mandatory pre-merge frontend checks (at minimum: build, lint, and
  targeted component/flow tests for retry, error rendering, and configuration
  actions).
- Require explicit evidence capture for UI policy controls (for example,
  manual-confirmation retry behavior and dismissable error-state handling).
- Record pass/fail outcomes in operation notes so frontend behavior changes are
  traceable in the same way as backend benchmark evidence.

Current status: pending implementation; must be completed as part of the next
frontend reliability pass.

## Telemetry retry behavior update (2026-04-14)

This note supersedes the earlier "no automatic retries" guidance for telemetry
operations.

- Telemetry writes are now auto-retried on failure.
- The frontend retry button flow needs adaptation to remove confirmation text
  noise from the user-facing path while preserving clear failure visibility.
- The current library/runtime wiring should be overhauled to support periodic
  background tasks (for example scheduled telemetry flush/retry), rather than
  relying on ad-hoc trigger points.

Implementation follow-up required:

- Update retry UX copy and interaction model so retry controls are explicit but
  low-noise.
- Introduce a periodic task/scheduler capability in the library layer for
  recurring telemetry responsibilities.

## Folder subsummary exposure follow-up

Generated folder-analysis subsummaries are now persisted, but they still need
to be exposed through a dedicated retrieval/discovery mechanism.

Implementation note:

- Add an explicit interface to surface subsummaries (for example API endpoint,

## Development-model pivot note

As of this commit, the project is pivoting toward a markdown-centric,
agent-based development model. Under this direction, implementation intent,
operational policy, planning artifacts, and report-ready outputs are to be
maintained primarily as versioned markdown documents that can guide and
constrain subsequent agent-assisted development work.
  indexed query path, or summary-browser view), with the final mechanism to be
  determined in a later design pass.

Progress update:

- A provisional mechanism is now implemented:
  - Backend endpoint: `GET /api/project/{id}/folder-subsummaries?entryUuid=...`
  - Frontend action in folder preview: `Show Subsummaries`
- This exposes persisted file-level subsummaries for the selected folder while
  a richer long-term discovery UX is still open for future design.
- Automated verification added:
  - `ProjectApiResourceSubsummaryTest` covers folder-path UUID resolution and
    ensures only summaries under the requested folder are returned.
