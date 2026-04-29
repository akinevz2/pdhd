# BenchLam - Ollama Benchmarking Harness

BenchLam is a lightweight benchmarking harness designed to evaluate the performance and correctness of models running on Ollama.

## Features

- **Automated Model Discovery**: Automatically detects available models via `ollama list`.
- **Flexible Verification**:
  - **Regex**: Check for specific patterns in the response.
  - **LLM-as-a-Judge**: Use a designated "judge" model to evaluate responses based on a provided rubric.
  - **Simple Match**: Check for exact substring presence.
- **Comprehensive Reporting**: Generates CSV logs, Markdown leaderboards, and SQLite tables with latency and accuracy metrics.

## Installation

1. Ensure [Ollama](https://ollama.com/) is installed and running.
2. Ensure Python 3 is installed.
3. Install dependencies:
   ```bash
   pip install requests
   ```

## Usage

1. Define your test cases in `scripts/benchlam/test_cases.json`.
2. Run the benchmark script:
   ```bash
   python3 scripts/benchlam/benchmark_ollama.py
   ```
3. View results in `scripts/benchlam/results/`.

### Model Selection Switches

Benchmark all models available on a specific Ollama host:

```bash
python3 scripts/benchlam/benchmark_ollama.py --host http://host.docker.internal:11434 --all-models
```

Benchmark a comma-separated override set only. Missing models are pulled first on that host:

```bash
python3 scripts/benchlam/benchmark_ollama.py --host host.docker.internal:11434 --models llama3,mistral:7b
```

Benchmark a comma-separated override set, but do not pull missing models:

```bash
python3 scripts/benchlam/benchmark_ollama.py --host host.docker.internal:11434 --models llama3,mistral:7b --skip-pull
```

Notes:

- `--models` takes precedence over `--all-models`.
- If neither switch is supplied, the script benchmarks all currently available models on the selected host.
- `--skip-pull` only affects `--models`; missing override models are skipped instead of being pulled.

## SQLite Logging

Each benchmark run is also persisted to:

- `scripts/benchlam/results/benchmark_results.sqlite`

This enables cross-run SQL analysis for academic reporting.

### Tables

- `benchmark_runs`: one row per benchmark execution.
- `benchmark_results`: one row per `(model, test_case)` result in a run.
- `benchmark_model_summary`: one row per `(run, model)` aggregate.
- `benchmark_host_snapshot`: host metadata for the run (host, resolved IP, Ollama version, reachable flag, and `/api/ps` VRAM indicators).

### Host Capability Notes

- Ollama does not expose raw `nvidia-smi` or `rocm-smi` output over its HTTP API.
- The script captures host-level metadata from:
  - `/api/version`
  - `/api/tags`
  - `/api/ps`
- VRAM-related fields are derived from `/api/ps` model entries (for example `size_vram` per running model), then stored as:
  - `total_running_size_vram_bytes`
  - `max_running_size_vram_bytes`

If you need full GPU inventory (device model, total VRAM, driver/runtime), collect that separately on each host (for example with `nvidia-smi` or `rocm-smi`) and join it in analysis by host name/IP.

### Example Queries

Compare per-model averages across all runs:

```sql
SELECT
  model_name,
  COUNT(*) AS run_count,
  AVG(avg_latency) AS mean_latency_s,
  AVG(avg_accuracy) AS mean_accuracy
FROM benchmark_model_summary
GROUP BY model_name
ORDER BY mean_accuracy DESC, mean_latency_s ASC;
```

Show trend by run date for a single model:

```sql
SELECT
  r.run_started_at,
  s.model_name,
  s.tests_run,
  s.avg_latency,
  s.avg_accuracy
FROM benchmark_model_summary s
JOIN benchmark_runs r ON r.run_id = s.run_id
WHERE s.model_name = 'llama3'
ORDER BY r.run_started_at;
```

Find hardest test cases (lowest pass rate) across runs:

```sql
SELECT
  test_case,
  AVG(correctness_score) AS pass_rate,
  COUNT(*) AS samples
FROM benchmark_results
GROUP BY test_case
HAVING COUNT(*) >= 3
ORDER BY pass_rate ASC, samples DESC;
```

## Test Case Format (`test_cases.json`)

```json
[
  {
    "name": "Test Name",
    "prompt": "The prompt to send to the model",
    "verification": {
      "type": "regex",
      "pattern": "Expected Pattern"
    }
  },
  {
    "name": "Judging Test",
    "prompt": "An open-ended prompt",
    "verification": {
      "type": "judge",
      "judge_model": "llama3",
      "rubric": "Evaluate if the response is polite. Return '1' for pass, 'n' for fail."
    }
  },
  {
    "name": "String Match Test",
    "prompt": "A simple prompt",
    "expected": "A specific word"
  }
]
```

## Scripts

| File                                         | Purpose                                                                                                                                                                                                                                                                                                     |
| -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [`benchmark_ollama.py`](benchmark_ollama.py) | Main entry point. Discovers available Ollama models via `ollama list`, runs every test case in `test_cases.json` against each model, verifies responses (regex / LLM-as-a-Judge / string match), and writes timestamped CSV, Markdown, and SQLite outputs to `results/`.                                    |
| [`test_cases.json`](test_cases.json)         | Test case definitions. Each entry declares a `name`, a `prompt`, and a `verification` strategy (`regex`, `judge`, or plain `expected` string). Edit this file to add or modify benchmark scenarios.                                                                                                         |
| [`results/`](results/)                       | Output directory. Populated at runtime with per-run CSV logs (`benchmark_results_<host>_<run_id>.csv`), Markdown leaderboard summaries (`leaderboard_<host>_<run_id>.md`), Maven test output (`maven-prompt-tests.txt`), and SQLite history (`benchmark_results.sqlite`). Not committed to version control. |
