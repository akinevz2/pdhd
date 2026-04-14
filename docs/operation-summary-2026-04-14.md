# Operation Summary (2026-04-14)

This summary captures the implementation work completed while stepping through
`docs/recommendations-for-implementation.md`.

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

## Commit record

| Field   | Value                                                      |
|---------|------------------------------------------------------------|
| Hash    | `eef03ab0ed2c4f16a791bf219f1de350d7805863`                 |
| Branch  | `feature/custom-tool-support`                              |
| Date    | 2026-04-14                                                 |
| Message | `chore: snapshot current repository state`                 |
| Stats   | 189 files changed, 11447 insertions(+), 9591 deletions(−) |

### Summary of changes included in this commit

- New support classes (`BackendSupport`, `ToolSupport`, `SchemaKeys`).
- Startup duplicate-tool-name validation in `ProjectAssistantProducer`.
- Telemetry schema guard in `TelemetryService`; typed payload / schema-version fields added to `ToolTelemetryRecord`.
- New tools: `ReadFileTools`, `WebSearchTools`, `WorkspaceContextTools`.
- Refactored AI services: `ProjectAssistant`, `ProjectAssistantProducer`, `FileSummarisationPipelineService`, `FileSummarisationSubagent`, `StructuredSummaryStoreService`, `WebUiChatMemoryProviderSupplier`.
- New REST resources: `ProjectApiResource`, `ToolTelemetryResource`, `WorkspaceApiResource`.
- Removed legacy RAG/embedding services and tools (embeddings, RAG policy, RAFT, folder-summary pipeline).
- Renamed `GithubFolder` → `GithubMetadata`; added `Workspace`, `StructuredSummary`, `SummaryType` entities.
- `PreCdiOllamaBootstrap` startup step added.
- `HtmlFrameWindow.tsx` component added to web UI.
- Benchmark script (`scripts/benchmark.sh`) with HTTP-status-aware pass/fail logic and configurable timeout.
- Evaluation docs: `benchmark-scenarios.md`, `environment-spec.md`, `decision-log.md`, two benchmark result artefacts.
- New docs: `api-listing.md`, `april-2026-feature-summary.md`, `cache-policy.md`, `implementation-checklist.md`, `ollama-fallback-completion-fix.md`, `operation-summary-2026-04-09.md`, `operation-summary-2026-04-14.md`.
- Removed stale docs: `nullability-report.md`, `tool-calling-architecture.md`, `tool-calling-conventions.md`.
- `.github/agents/` directory with four agent definition files added.
