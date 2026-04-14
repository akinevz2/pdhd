# Decision Log

Tracks design decisions that affect dispatch policy, schema contracts, or
cache semantics. Per the cross-phase operating rules from the execution plan,
a brief entry must be added for each such change.

---

## Format

```
### YYYY-MM-DD – <short title>
**Area:** dispatch | schema | cache | other
**Change:** what was changed
**Rationale:** why
**Evidence/links:** commit hash, issue, doc section
```

---

## Entries

### 2026-04-14 – Support-class standardisation

**Area:** schema / dispatch  
**Change:** Created `BackendSupport`, `ToolSupport`, and `SchemaKeys` classes
in `ac.uk.sussex.kn253.support`. Migrated hard-coded string literals from
`Origin`, `WebSearchTools`, `WorkspaceContextTools`, `ReadFileTools`, and
`GithubMetadataService` to these constants.  
**Rationale:** Recommendations §9 – centralise policy constants to prevent
duplication and enable controlled evolution.  
**Evidence/links:** see `docs/operation-summary-2026-04-14.md`

### 2026-04-14 – Startup duplicate tool-name check

**Area:** dispatch  
**Change:** `ProjectAssistantProducer` now observes `StartupEvent` and scans
all registered tool beans for `@Tool`-annotated method name collisions. Fails
fast if any duplicate is found.  
**Rationale:** Recommendations §3 – as the tool registry grows, first-match
dispatch can create silent regressions; explicit detection at startup prevents
that.  
**Evidence/links:** see `docs/operation-summary-2026-04-14.md`

### 2026-04-14 – Typed contract columns in tool_telemetry

**Area:** schema  
**Change:** Added `typed_output_payload` (TEXT) and `output_schema_version`
(INTEGER) columns to `ToolTelemetryRecord`. Added overloaded
`TelemetryService#recordToolUse` that accepts these fields.  
**Rationale:** Recommendations §2 – hybrid contract: human-readable string for
LLM, typed JSON for programmatic verification/analytics. Schema version field
supports safe evolution.  
**Evidence/links:** see `docs/operation-summary-2026-04-14.md`

### 2026-04-14 – Cache freshness policy documented

**Area:** cache  
**Change:** Created `docs/cache-policy.md` defining TTL, invalidation
triggers, and staleness risk for each cached content type.  
**Rationale:** Recommendations §4 – clear freshness semantics reduce stale
output risk and improve transparency in academic reporting.  
**Evidence/links:** `docs/cache-policy.md`

### 2026-04-14 – Telemetry startup guard

**Area:** schema  
**Change:** `TelemetryService` now observes `StartupEvent`, logs current row
counts for both telemetry tables, and emits a visible `ERROR` log if the
schema strategy is not `update`.  
**Rationale:** Recommendations §8 – make the non-destructive ORM constraint
visible and detectable in CI/operator logs.  
**Evidence/links:** see `docs/operation-summary-2026-04-14.md`

### 2026-04-14 – Benchmark runner correctness hardening

**Area:** other  
**Change:** Updated `scripts/benchmark.sh` to evaluate scenario success using
HTTP status (2xx required) and explicit backend/model error markers in
response content. Added `http_status` field to each result row and tightened
the S08 security scenario to fail if sensitive `/etc/passwd` style content
appears.  
**Rationale:** Initial benchmark artifacts could mark failed calls as success
when curl transport succeeded but API returned a 500 error page. This created
false-positive evidence and weakened evaluation credibility.  
**Evidence/links:** `scripts/benchmark.sh`,
`docs/evaluation/results/run-2026-04-14T04:18:41.json`,
`docs/operation-summary-2026-04-14.md`
