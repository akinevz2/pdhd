## 6.2 Recommendations for Implementation

To strengthen both practical deployment and academic quality, implementation should proceed as a staged roadmap with measurable checkpoints.

### 6. Calibrate scope with explicit risk management

Implementation planning should distinguish between immediate, medium-term, and exploratory work. A recommended sequencing model is:

- Phase A: reliability and correctness (dispatch checks, validation, error handling)
- Phase B: performance and scaling (latency optimization, cache policy tuning)
- Phase C: feature extension (new tools, richer orchestration, advanced routing)

Each phase should include entry/exit criteria so conclusions remain grounded in delivered capability rather than intent.

### Implementation progress note (2026-04-14)

Recommendations 1, 2, 3, 4, 5, 7, 8, and 9 have been implemented and moved to completion artifacts:

- `docs/evaluation/benchmark-scenarios.md`
- `scripts/benchlam/benchmark_ollama.py`
- `docs/cache-policy.md`
- `docs/evaluation/environment-spec.md`
- `docs/evaluation/decision-log.md`
- `docs/implementation-checklist.md`
- `docs/operation-summary-2026-04-14.md`

Recommendation 6 remains as the active planning section in this file.

### 11. Execution Plan Addendum (2026-04 to 2026-06)

This addendum converts the recommendations above into an implementation sequence with explicit outputs and acceptance checks.

#### Phase 1: Reliability Baseline (2 weeks)

Goal: ensure core tool execution and API boundaries are predictable before extending features.

Deliverables:

- Duplicate tool-name detection at startup with clear failure messages.
- Centralized dispatch precedence policy and short maintainer documentation.
- Per-tool telemetry fields for duration, validation errors, and execution outcome.
- Smoke-test script covering: list directory, read file, summarize folder.

Acceptance criteria:

- Zero ambiguous tool resolution at startup in default configuration.
- Smoke-test suite passes end-to-end on a clean run.
- Telemetry records exist for each tool invocation path used by smoke tests.

Evidence to capture:

- Startup logs showing duplicate-check status.
- Test output for smoke suite with pass/fail summary.
- Sample telemetry rows for each exercised tool.

#### Phase 2: Evidence and Evaluation Loop (2 weeks)

Goal: make reportable claims measurable and repeatable.

Deliverables:

- Compact benchmark scenario set (at least 8 representative tasks).
- Baseline metrics capture script (P50/P95 latency, tool failure rate, multi-step task success).
- Versioned benchmark input set stored with date and runtime configuration.

Acceptance criteria:

- Benchmark script runs without manual intervention.
- Baseline metrics file produced in machine-readable format.
- At least one repeated run showing metric stability or variance bounds.

Evidence to capture:

- Benchmark scenario definitions.
- Baseline metrics artifacts from at least two runs.
- Hardware/runtime profile used for each run.

#### Phase 3: Cache and Contract Hardening (2 weeks)

Goal: reduce stale-output risk and improve backend/frontend contract clarity.

Deliverables:

- Cache TTL policy by content type.
- Invalidation hooks on write/refresh operations.
- Hybrid tool responses: human-readable summary plus typed payload.
- Response schema version field for key endpoints.

Acceptance criteria:

- Cache freshness behavior documented and test-covered.
- No schema-breaking changes for existing frontend paths.
- Typed payload available for all high-frequency tool operations.

Evidence to capture:

- Contract examples before/after schema versioning.
- Cache policy table and test output.
- Regression results for existing frontend interactions.

#### Phase 4: Feature Extension with Guardrails (2 weeks)

Goal: extend capability only after reliability and measurement controls are in place.

Deliverables:

- One additional high-value tool category (for example repository metadata enrichment).
- Host-policy extension hooks prepared for non-GitHub forge links.
- Updated operator-facing error messages for new failure modes.

Acceptance criteria:

- New tool category integrated without increased baseline failure rate.
- Existing scenarios remain green in benchmark suite.
- Known-issues list updated with any newly discovered constraints.

Evidence to capture:

- Delta metrics vs. Phase 2 baseline.
- New tool integration tests.
- Updated issue and decision-log entries.

#### Cross-phase operating rules

- Do not merge feature work that reduces telemetry continuity.
- Record a brief decision log entry for each change to dispatch policy, schema contract, or cache semantics.
- Gate phase transitions on acceptance criteria, not calendar date.
