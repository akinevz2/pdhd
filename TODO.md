# TODO

_Last updated: 2026-04-26_

## Open Issues

- [ ] **Folder summary evidence leakage** — `read_folder_manifest` evidence markers (e.g. `=== sampled file contents (evidence only) ===`) are reproduced in LLM responses and rendered raw in the explorer canvas. Fix options (ranked by effort): system-prompt clarification → response post-processing strip → backend narrative/evidence separation. See `docs/session-3-issues.md` and `docs/known-issues.md`.

## Documentation Gaps

- [ ] `quick-start.md` — referenced in `docs/known-issues.md` but not yet created.
- [ ] `developer-guide.md` — referenced in `docs/known-issues.md` but not yet created.
- [ ] `PROJECT_MANIFEST.md` — still contains placeholder text (`[Your Name]`, template rows). Fill in actual module table and project vision.

## Missing Build Reports

- [ ] **2026-03-30 session report** — ergonomics pass (magic strings, signal payload constants, `ToolSupport` schema catalog). Operation summary exists in `archive/` but no feature report in `docs/`.
- [ ] **2026-04-05 session report** — reliability pass (fail-fast menu propagation, dynamic Ollama client resolution, configuration menu fixes). Operation summary exists in `archive/` but no feature report in `docs/`.
- [ ] **2026-04-15 session report** — recommendations 1–10 implementation (telemetry, dispatch safety, support-class standardisation, benchmark baseline, subsummary endpoint). `docs/implementation-checklist.md` covers outcomes but no narrative feature report exists in `docs/`.

## Tool Stability (Benchmark Blocker)

Benchmarking is frozen. 11 runs on 2026-04-14 against `gemma4:latest` at `host.docker.internal:11434` exposed two compounding failure modes:

**Failure mode 1 — Model does not invoke tools (CDI proxy echo)**
Scenarios S01 and S02 (working directory, open projects) consistently returned the Quarkus CDI subclass name as the answer:

```
ac.uk.sussex.kn253.tools.WorkspaceContextTools$$QuarkusInvoker$getCurrentWorkingDirectory_59dfe...
```

The model saw the proxy class name in the tool registration surface and echoed it rather than calling the tool. Tool invocation did not occur at all.

**Failure mode 2 — Fabricated content when tools appeared to execute**

- S04: "I cannot find a file named README.md" (file exists), or a fabricated generic Quarkus README unrelated to PDHD.
- S05: Reported top-level package as `com` — wrong; actual package is `ac`.
- S06: Described `TelemetryService` methods (`recordApiCallDuration`, `recordUserInteraction`) that do not exist in the class.
- S07: Returned `example.com` / `devblog.example.com` fabricated URLs — web search tool not called or output fabricated.

**Failure mode 3 — Model crash / Ollama instability mid-run**
Multiple runs produced `UnresolvedModelServerException` (HTTP 500) from LangChain4j after the first scenario, indicating `gemma4:latest` crashed or was evicted under load at the host.

**S08 — Only consistently passing scenario**
Security boundary enforcement (`/etc/passwd` access denied) passed in all runs where the app was reachable. This is a service-layer guarantee, not model-dependent.

**Root causes (in order of confidence)**

1. `@Tool` and `@P` descriptions are underspecified — the model cannot reliably identify which tool to call or how to construct arguments.
2. `DEFAULT_TOOL_SYSTEM_PROMPT` (single sentence) provides no grounding: no tool list, no path conventions, no error-string contract.
3. `gemma4:latest` may lack robust function-calling support; a tool-calling-capable model (e.g. `qwen2.5`, `mistral-nemo`) should be validated as an alternative.

- [x] **Audit and rewrite `@Tool`/`@P` descriptions** — Completed. All three tool classes grounded; explicit `@Tool(name=...)` added to all fallback methods. Fabrication-prevention wording removed (not a security control).
- [x] **Replace `AiServices` tool dispatch with low-level implicit-context loop** — Completed in `services.ai` using manual `ChatRequest` + `ToolExecutionResultMessage` orchestration with eager implicit context injection for `getCurrentWorkingDirectory` and `getOpenProjectDirectories`. `./mvnw compile` passes (2026-04-26).
- [x] **Harden `DEFAULT_TOOL_SYSTEM_PROMPT` in `LLMSettings`** — Completed with explicit tool dispatch names, path convention guidance, failed-call error-string contract, and a 4-call sequential cap. `./mvnw compile` passes (2026-04-26).
- [x] **Pull models on ws-raretower** — All three pulled successfully (confirmed 2026-04-26). ws-raretower now has: `llama3.1:latest`, `qwen3.6:27b-q4_K_M`, `qwen3.6:latest`, `gemma4:31b-it-q4_K_M`, `gemma4:26b`.
- [ ] **Run benchmark — leg 1 (local host)** — Run from inside the dev container once PDHD app is running in dev mode. Uses `host.docker.internal:11434`. All three cross-host models are present locally.
  ```
  python3 scripts/benchlam/benchmark_ollama.py \
      --host http://host.docker.internal:11434 \
      --models llama3.1:latest qwen3.6:latest qwen3.6:27b-q4_K_M \
      --skip-pull \
      --test-cases scripts/benchlam/pdhd_test_cases.json
  ```
  Results written to `scripts/benchlam/results/benchmark_results.sqlite`.
- [ ] **Run benchmark — leg 2 (ws-raretower)** — Route confirmed via port forward: `http://ws-raretower:11434` is reachable from the dev container. Run after all three ws-raretower pulls complete. Cross-host comparison model is `llama3.1:latest`.
  ```
  python3 scripts/benchlam/benchmark_ollama.py \
      --host http://ws-raretower:11434 \
      --models llama3.1:latest qwen3.6:latest qwen3.6:27b-q4_K_M \
      --skip-pull \
      --test-cases scripts/benchlam/pdhd_test_cases.json
  ```
- [ ] **Generate comparative graphs** — Once both legs are recorded in the same SQLite DB, run the plot script from inside the dev container:
  ```
  python3 scripts/matplot/plot_benchmarks.py
  ```
  Outputs to `scripts/matplot/output/`. `HOST_LABELS` now maps `host.docker.internal` → `minifridge` and `192.168.137.55`/`ws-raretower` → `ws-raretower`.
- [ ] **Enforce CODESTYLE on tool try-finally telemetry** — Manual `try-finally` blocks in all three tool classes violate `CODESTYLE.md`. Tracked as follow-on after stability work.

## Benchmark / Evaluation

- [ ] Run benchmark — see two-phase procedure above. **Unblocked** — all models confirmed on both hosts (2026-04-26). Ready to run legs 1 and 2.
- [ ] Review S08 security scenario pass rate after the HTTP-status-aware fix applied 2026-04-14 — **Blocked** pending the post-validation benchmark run.
