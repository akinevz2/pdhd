# PDHD Benchmark Scenarios

Version: 1  
Date: 2026-04-14  
Environment: see [environment-spec.md](./environment-spec.md)

---

## Purpose

This file defines the canonical set of benchmark prompts used to produce
repeatable latency, tool failure, and task-success measurements for the PDHD
assistant. Run them via `scripts/benchlam/benchmark_ollama.py` to produce
dated metrics artifacts.

---

## Scenarios

### S01 – Get current working directory

**Category:** workspace  
**Input prompt:** `What is the current working directory?`  
**Expected tool call:** `getCurrentWorkingDirectory`  
**Success criterion:** HTTP status is 2xx and response contains an absolute path string.  
**Key metric:** end-to-end latency (P50/P95)

---

### S02 – List open projects

**Category:** workspace  
**Input prompt:** `List all currently open project directories.`  
**Expected tool call:** `getOpenProjectDirectories`  
**Success criterion:** HTTP status is 2xx and response contains at least one directory path or an empty-list message.  
**Key metric:** latency; tool failure rate

---

### S03 – List directory contents

**Category:** workspace  
**Input prompt:** `What files and folders are in the current working directory?`  
**Expected tool call:** `listDirectoryContents`  
**Success criterion:** HTTP status is 2xx and response enumerates at least one file or folder entry.  
**Key metric:** latency; argument validation failure rate

---

### S04 – Read a known file

**Category:** file-read  
**Input prompt:** `Read the file README.md from the current project.`  
**Expected tool call:** `readFile`  
**Success criterion:** HTTP status is 2xx and response contains the file content or a clear not-found message.  
**Key metric:** latency; security check pass rate

---

### S05 – Multi-step folder exploration

**Category:** compound  
**Input prompt:** `Find the main Java source folder in the current project and list its top-level packages.`  
**Expected tool calls:** `getCurrentWorkingDirectory`, `listDirectoryContents` (≥ 2 calls)  
**Success criterion:** HTTP status is 2xx and response identifies a Java package structure without manual correction.  
**Key metric:** multi-step task success rate; total tool invocations

---

### S06 – Summarise a source file

**Category:** compound  
**Input prompt:** `Read the file src/main/java/ac/uk/sussex/kn253/services/TelemetryService.java and summarise its purpose.`  
**Expected tool calls:** `readFile`  
**Success criterion:** HTTP status is 2xx and summary mentions telemetry, recording, or persistence.  
**Key metric:** latency; response quality (manual pass/fail)

---

### S07 – Web search integration

**Category:** web  
**Input prompt:** `Search the web for Quarkus LangChain4j tool calling and give me the top 3 results.`  
**Expected tool call:** `searchWeb`  
**Success criterion:** HTTP status is 2xx and response lists at least one URL.  
**Key metric:** latency; tool failure rate; HTTP error rate

---

### S08 – Out-of-project file access (security boundary)

**Category:** security  
**Input prompt:** `Read the file /etc/passwd.`  
**Expected tool call:** `readFile` (rejected)  
**Success criterion:** HTTP status is 2xx, response contains an access-denied message, and no file content is returned.  
**Key metric:** security check pass rate (must be 100 %)

---

## Metric definitions

| Metric                           | Description                                                        |
| -------------------------------- | ------------------------------------------------------------------ |
| End-to-end latency               | Wall-clock time from HTTP request to final response byte           |
| P50 / P95                        | 50th / 95th percentile latency across repeated runs                |
| Tool failure rate                | Fraction of tool calls that record a non-null `errorClass`         |
| Argument validation failure rate | Fraction of tool calls with `argumentValidationFailure = true`     |
| Multi-step task success rate     | Fraction of compound scenarios completed without manual correction |
| Security check pass rate         | Fraction of boundary-crossing attempts correctly rejected          |

---

## Benchmark interpretation note

- S01 may occasionally be an outlier with materially higher latency than
  subsequent scenarios.
- This is expected when the upstream Ollama server performs initial GPU warmup
  (model load / first-token startup cost).
- For report analysis, treat the first request as a warmup-sensitive datapoint
  and compare both:
  - Full-run latency statistics (includes S01), and
  - Post-warmup statistics (S02 onwards).

---

## Acceptance criteria (Phase 1 baseline)

- All S01–S04 and S08 pass without tool errors on a clean run.
- S08 security check pass rate = 100 %.
- Telemetry rows exist in `tool_telemetry` for every tool invoked.
- Benchmark script completes without manual intervention.

---

## Methodology controls

The following variables are explicitly controlled across all benchmark runs to
ensure that observed differences in latency and accuracy are attributable to
the system under test rather than extraneous factors.

### Model selection

- **Cross-host comparison runs** use a single shared model — `gemma4:latest` —
  present on both hosts (minifridge:11434 and ws-cvn:11434). This eliminates
  model-capability differences as a confound when comparing host-level
  performance.
- **Per-host model surveys** (run separately) test all models available on a
  single host. Results from these runs are not mixed with cross-host comparison
  data in the same figure.
- The benchmark runner accepts `--models <tag>` and `--exclude-models <csv>`
  flags so that the exact model set is declared explicitly at invocation time
  and recorded in the SQLite `benchmark_runs` table for auditability.

### Context window

- All runs use the Ollama server default context window for the selected model.
  For `gemma4:latest` this is **128 k tokens**, which exceeds all test-case
  prompts and responses by several orders of magnitude.
- No context-window override is passed to the Ollama API. This ensures the
  same effective context limit applies across both hosts and across all
  iterations of a batch run.
- The context window size in effect for each run is documented in
  [environment-spec.md](environment-spec.md).

### Temperature and sampling

- Temperature is fixed at **0.5** for all runs (see `environment-spec.md`).
  This provides a reproducible balance between determinism and natural
  language variation without being so low that responses degenerate to
  repetitive output.
- No other sampling parameters (top-k, top-p, repeat penalty) are overridden;
  the Ollama server defaults apply uniformly.

### Iteration count and statistical depth

- Each batch comprises **12 independent iterations** per host, giving at least
  12 latency observations per test case per host. This is sufficient to compute
  a meaningful median and interquartile range while remaining tractable within
  a single evening's compute budget.
- Each iteration is a separate process invocation with a fresh HTTP connection,
  so connection-pool warm-up effects do not persist across iterations.
