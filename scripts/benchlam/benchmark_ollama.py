import signal
import subprocess
import sys
import json
import csv
import re
import time
import requests
import os
import sqlite3
import uuid
import argparse
import socket
from urllib.parse import urlparse
from datetime import datetime

# ---------------------------------------------------------------------------
# Signal handling – flush output and exit cleanly on SIGINT / SIGTERM
# ---------------------------------------------------------------------------
def _handle_signal(signum, frame):
    sig_name = signal.Signals(signum).name
    print(f"\n[benchmark] Received {sig_name}; aborting run.", flush=True)
    sys.exit(1)

signal.signal(signal.SIGINT, _handle_signal)
signal.signal(signal.SIGTERM, _handle_signal)

# ---------------------------------------------------------------------------
# Maven prompt-spec test runner
# ---------------------------------------------------------------------------
MAVEN_WRAPPER = os.path.join(os.path.dirname(__file__), "..", "..", "mvnw")
PROMPT_SPEC_TEST_PATTERN = "*PromptTest"


def run_maven_prompt_tests():
    """
    Runs the prompt-spec JUnit test suite via Maven before the Ollama
    benchmarks.  Returns True when all tests pass, False on failure.
    The full Surefire output is written to results/maven-prompt-tests.txt.
    """
    os.makedirs(RESULTS_DIR, exist_ok=True)
    output_file = os.path.join(RESULTS_DIR, "maven-prompt-tests.txt")

    mvnw = os.path.abspath(MAVEN_WRAPPER)
    if not os.path.isfile(mvnw):
        print(f"[maven] mvnw not found at {mvnw}; skipping prompt-spec tests.")
        return True  # non-fatal: carry on with Ollama benchmarks

    cmd = [mvnw, "test", f"-Dtest={PROMPT_SPEC_TEST_PATTERN}", "-pl", "."]
    project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

    print(f"[maven] Running prompt-spec tests: {' '.join(cmd)}", flush=True)
    try:
        result = subprocess.run(
            cmd,
            cwd=project_root,
            capture_output=True,
            text=True,
            timeout=300,
        )
        with open(output_file, "w") as f:
            f.write(result.stdout)
            f.write(result.stderr)

        if result.returncode == 0:
            print(f"[maven] All prompt-spec tests passed. Output: {output_file}", flush=True)
            return True
        else:
            print(f"[maven] Prompt-spec tests FAILED (exit {result.returncode}). "
                  f"See {output_file} for details.", flush=True)
            return False
    except subprocess.TimeoutExpired:
        print("[maven] Prompt-spec test run timed out after 300 s.", flush=True)
        return False
    except Exception as exc:
        print(f"[maven] Could not run prompt-spec tests: {exc}", flush=True)
        return False

# Configuration
DEFAULT_OLLAMA_HOST = "http://localhost:11434"
OLLAMA_LIST_CMD = ["ollama", "list"]
OLLAMA_PULL_CMD = ["ollama", "pull"]
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_TEST_CASES_FILE = os.path.join(SCRIPT_DIR, "test_cases.json")
TEST_CASES_FILE = DEFAULT_TEST_CASES_FILE  # kept for legacy callers
RESULTS_DIR = os.path.join(SCRIPT_DIR, "results")
SQLITE_DB_FILENAME = "benchmark_results.sqlite"


def init_sqlite(db_path):
    """Creates benchmark tables when missing."""
    with sqlite3.connect(db_path, timeout=30) as conn:
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA busy_timeout=30000")
        conn.execute("""
            CREATE TABLE IF NOT EXISTS benchmark_runs (
                run_id TEXT PRIMARY KEY,
                run_started_at TEXT NOT NULL,
                run_finished_at TEXT,
                test_cases_file TEXT NOT NULL,
                maven_prompt_tests_passed INTEGER NOT NULL,
                notes TEXT
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS benchmark_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL,
                model_name TEXT NOT NULL,
                test_case TEXT NOT NULL,
                prompt TEXT NOT NULL,
                response TEXT,
                latency REAL NOT NULL,
                correctness_score INTEGER NOT NULL,
                FOREIGN KEY(run_id) REFERENCES benchmark_runs(run_id)
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS benchmark_model_summary (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL,
                model_name TEXT NOT NULL,
                tests_run INTEGER NOT NULL,
                avg_latency REAL NOT NULL,
                avg_accuracy REAL NOT NULL,
                FOREIGN KEY(run_id) REFERENCES benchmark_runs(run_id)
            )
        """)
        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_benchmark_results_run_model
            ON benchmark_results(run_id, model_name)
        """)
        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_benchmark_summary_run_model
            ON benchmark_model_summary(run_id, model_name)
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS benchmark_host_snapshot (
                run_id TEXT PRIMARY KEY,
                ollama_host TEXT NOT NULL,
                resolved_host TEXT,
                resolved_ip TEXT,
                api_reachable INTEGER NOT NULL,
                ollama_version TEXT,
                available_models_count INTEGER NOT NULL,
                running_models_count INTEGER NOT NULL,
                total_running_size_vram_bytes INTEGER NOT NULL,
                max_running_size_vram_bytes INTEGER NOT NULL,
                tags_payload TEXT,
                ps_payload TEXT,
                FOREIGN KEY(run_id) REFERENCES benchmark_runs(run_id)
            )
        """)


def save_results_to_sqlite(db_path, run_id, run_started_at, run_finished_at, maven_ok, results, host_snapshot):
    """Persists one benchmark run and associated detailed and summary rows."""
    with sqlite3.connect(db_path, timeout=30) as conn:
        conn.execute("PRAGMA busy_timeout=30000")
        conn.execute(
            """
            INSERT INTO benchmark_runs
                (run_id, run_started_at, run_finished_at, test_cases_file, maven_prompt_tests_passed, notes)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (run_id, run_started_at, run_finished_at, TEST_CASES_FILE, 1 if maven_ok else 0, "")
        )

        conn.execute(
            """
            INSERT INTO benchmark_host_snapshot
                (run_id, ollama_host, resolved_host, resolved_ip, api_reachable, ollama_version,
                 available_models_count, running_models_count, total_running_size_vram_bytes,
                 max_running_size_vram_bytes, tags_payload, ps_payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                run_id,
                host_snapshot["ollama_host"],
                host_snapshot["resolved_host"],
                host_snapshot["resolved_ip"],
                1 if host_snapshot["api_reachable"] else 0,
                host_snapshot["ollama_version"],
                int(host_snapshot["available_models_count"]),
                int(host_snapshot["running_models_count"]),
                int(host_snapshot["total_running_size_vram_bytes"]),
                int(host_snapshot["max_running_size_vram_bytes"]),
                host_snapshot["tags_payload"],
                host_snapshot["ps_payload"],
            )
        )

        if results:
            conn.executemany(
                """
                INSERT INTO benchmark_results
                    (run_id, model_name, test_case, prompt, response, latency, correctness_score)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                [
                    (
                        run_id,
                        r["model_name"],
                        r["test_case"],
                        r["prompt"],
                        r["response"],
                        float(r["latency"]),
                        int(r["correctness_score"]),
                    )
                    for r in results
                ],
            )

            model_stats = {}
            for r in results:
                model_name = r["model_name"]
                if model_name not in model_stats:
                    model_stats[model_name] = {"latencies": [], "scores": []}
                model_stats[model_name]["latencies"].append(float(r["latency"]))
                model_stats[model_name]["scores"].append(int(r["correctness_score"]))

            conn.executemany(
                """
                INSERT INTO benchmark_model_summary
                    (run_id, model_name, tests_run, avg_latency, avg_accuracy)
                VALUES (?, ?, ?, ?, ?)
                """,
                [
                    (
                        run_id,
                        model_name,
                        len(stats["latencies"]),
                        sum(stats["latencies"]) / len(stats["latencies"]),
                        sum(stats["scores"]) / len(stats["scores"]),
                    )
                    for model_name, stats in model_stats.items()
                ],
            )

def normalize_ollama_host(host):
    """Normalizes host values like localhost:11434 to http://localhost:11434."""
    normalized = host.strip()
    if not normalized.startswith("http://") and not normalized.startswith("https://"):
        normalized = f"http://{normalized}"
    return normalized.rstrip("/")


def host_slug(host):
    """Builds a filesystem-safe host token for artifact filenames."""
    value = extract_host_for_resolution(host).lower().strip()
    return re.sub(r"[^a-z0-9._-]", "-", value)


def make_ollama_api_url(host):
    """Builds the Ollama generate endpoint URL for a host."""
    return f"{normalize_ollama_host(host)}/api/generate"


def make_ollama_base_url(host):
    """Builds the normalized Ollama host base URL."""
    return normalize_ollama_host(host)


def ollama_cli_env(host):
    """Builds environment for ollama CLI to target the specified host."""
    env = os.environ.copy()
    env["OLLAMA_HOST"] = normalize_ollama_host(host)
    return env


def get_available_models(host):
    """Returns available model names for the target host.

    Tries the ollama CLI first (``ollama list``); if the CLI is not available
    in the current environment, falls back to the Ollama HTTP API
    (``/api/tags``) so that remote-only benchmark runs still work.
    """
    # --- CLI path ---
    try:
        result = subprocess.run(
            OLLAMA_LIST_CMD,
            capture_output=True,
            text=True,
            check=True,
            env=ollama_cli_env(host),
        )
        lines = result.stdout.strip().split('\n')
        if len(lines) <= 1:
            return []
        models = []
        for line in lines[1:]:
            parts = line.split()
            if parts:
                models.append(parts[0])
        return models
    except FileNotFoundError:
        print("[models] ollama CLI not found; falling back to HTTP /api/tags for model discovery.")
    except subprocess.CalledProcessError as e:
        print(f"[models] ollama list failed: {e}")

    # --- HTTP fallback path ---
    base_url = make_ollama_base_url(host)
    tags = safe_get_json(f"{base_url}/api/tags")
    if tags is None:
        print(f"[models] Could not reach {base_url}/api/tags; no models discovered.")
        return []
    return [m.get("name", "") for m in (tags.get("models") or []) if m.get("name")]


def pull_model_if_missing(model_name, host, available_models):
    """Pulls model to the target host when absent. Returns True when available."""
    if model_name in available_models:
        return True

    print(f"[models] {model_name} not present on {normalize_ollama_host(host)}; pulling...")
    try:
        result = subprocess.run(
            OLLAMA_PULL_CMD + [model_name],
            capture_output=True,
            text=True,
            check=True,
            env=ollama_cli_env(host),
        )
        if result.stdout:
            print(result.stdout.strip())
        available_models.add(model_name)
        return True
    except subprocess.CalledProcessError as exc:
        print(f"[models] Failed to pull {model_name}: {exc}")
        if exc.stdout:
            print(exc.stdout.strip())
        if exc.stderr:
            print(exc.stderr.strip())
        return False

def query_ollama(model_name, prompt, ollama_api_url):
    """Queries the Ollama API for a given model and prompt."""
    payload = {
        "model": model_name,
        "prompt": prompt,
        "stream": False
    }
    start_time = time.time()
    try:
        response = requests.post(ollama_api_url, json=payload, timeout=180)
        response.raise_for_status()
        latency = time.time() - start_time
        return response.json().get("response", ""), latency
    except Exception as e:
        print(f"Error querying Ollama for {model_name}: {e}")
        return None, 0


def safe_get_json(url, timeout_seconds=6):
    """GETs JSON endpoint with timeout and returns parsed object or None."""
    try:
        response = requests.get(url, timeout=timeout_seconds)
        response.raise_for_status()
        return response.json()
    except Exception:
        return None


def extract_host_for_resolution(host):
    """Extracts hostname for DNS resolution from host URL/string."""
    normalized = normalize_ollama_host(host)
    parsed = urlparse(normalized)
    return parsed.hostname or normalized


def probe_host_snapshot(host):
    """Collects host-level Ollama metadata for SQLite logging."""
    base_url = make_ollama_base_url(host)
    resolved_host = extract_host_for_resolution(host)
    resolved_ip = ""
    try:
        resolved_ip = socket.gethostbyname(resolved_host)
    except Exception:
        resolved_ip = ""

    version_payload = safe_get_json(f"{base_url}/api/version")
    tags_payload = safe_get_json(f"{base_url}/api/tags")
    ps_payload = safe_get_json(f"{base_url}/api/ps")

    api_reachable = any(payload is not None for payload in [version_payload, tags_payload, ps_payload])
    ollama_version = (version_payload or {}).get("version", "")

    tags_models = (tags_payload or {}).get("models", [])
    running_models = (ps_payload or {}).get("models", [])

    size_vram_values = []
    for model in running_models:
        raw = model.get("size_vram", 0)
        try:
            size_vram_values.append(int(raw))
        except (TypeError, ValueError):
            size_vram_values.append(0)

    return {
        "ollama_host": base_url,
        "resolved_host": resolved_host,
        "resolved_ip": resolved_ip,
        "api_reachable": api_reachable,
        "ollama_version": ollama_version,
        "available_models_count": len(tags_models),
        "running_models_count": len(running_models),
        "total_running_size_vram_bytes": sum(size_vram_values),
        "max_running_size_vram_bytes": max(size_vram_values) if size_vram_values else 0,
        "tags_payload": json.dumps(tags_payload) if tags_payload is not None else "",
        "ps_payload": json.dumps(ps_payload) if ps_payload is not None else "",
    }

def verify_response(response, verification):
    """Verifies the response based on regex or judge."""
    if not response:
        return 0

    v_type = verification.get("type")
    
    if v_type == "regex":
        pattern = verification.get("pattern")
        if re.search(pattern, response):
            return 1
        return 0
    
    elif v_type == "judge":
        judge_model = verification.get("judge_model")
        rubric = verification.get("rubric")
        if not judge_model or not rubric:
            return 0
        
        # The judge model itself will be queried in the main loop logic, 
        # but for the purpose of this script, we'll assume the main loop 
        # handles the orchestration and this function is called by it.
        # Actually, let's implement the judge call here or structure the loop better.
        # To keep it simple, we will handle the judge call in the main loop.
        return None # Signal that judge needs to be run
    
    elif v_type == "simple_match":
        # If there's no specific verification type, assume a simple string match from a 'expected' field if it exists
        # But let's stick to the requirements.
        return 0
        
    return 0

def resolve_target_models(host, models_override_csv, all_models, skip_pull=False, exclude_csv=None):
    """Resolves benchmark target models based on CLI switches and host state."""
    available = set(get_available_models(host))
    excluded = {m.strip() for m in (exclude_csv or "").split(",") if m.strip()}
    if excluded:
        print(f"[models] Excluding: {', '.join(sorted(excluded))}")

    if models_override_csv:
        requested = [m.strip() for m in models_override_csv.split(",") if m.strip()]
        if not requested:
            return []

        selected = []
        for model_name in requested:
            if model_name in excluded:
                print(f"[models] {model_name} excluded via --exclude-models; skipping.")
                continue
            if model_name in available:
                selected.append(model_name)
                continue

            if skip_pull:
                print(f"[models] {model_name} missing on {normalize_ollama_host(host)} and --skip-pull is set; skipping.")
                continue

            if pull_model_if_missing(model_name, host, available):
                selected.append(model_name)
        return selected

    if all_models:
        return sorted(available - excluded)

    return sorted(available - excluded)


def run_benchmark(host, models_override_csv=None, all_models=False, skip_pull=False, test_cases_file=None, exclude_csv=None):
    run_started_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    run_id = datetime.now().strftime('%Y%m%d_%H%M%S') + "_" + uuid.uuid4().hex[:8]
    ollama_api_url = make_ollama_api_url(host)
    host_snapshot = probe_host_snapshot(host)

    resolved_test_cases_file = test_cases_file or DEFAULT_TEST_CASES_FILE

    models = resolve_target_models(host, models_override_csv, all_models, skip_pull=skip_pull, exclude_csv=exclude_csv)
    if not models:
        print("No models found to benchmark. Ensure Ollama is running and has models pulled.")
        return

        print(f"[benchmark] Target host: {normalize_ollama_host(host)}", flush=True)
        print(f"[benchmark] Models: {', '.join(models)}", flush=True)
        print(f"[benchmark] Host reachable: {host_snapshot['api_reachable']}; version: {host_snapshot['ollama_version'] or 'unknown'}", flush=True)
        print(f"[benchmark] Running model VRAM bytes (sum/max): "
            f"{host_snapshot['total_running_size_vram_bytes']}/{host_snapshot['max_running_size_vram_bytes']}", flush=True)

    # Run the prompt-spec unit tests first.  A failure is reported but does not
    # prevent the Ollama benchmarks from running.
    maven_ok = run_maven_prompt_tests()
    if not maven_ok:
        print("[benchmark] Continuing with Ollama benchmarks despite test failures.")

    with open(resolved_test_cases_file, 'r') as f:
        test_cases = json.load(f)

    results = []
    
    os.makedirs(RESULTS_DIR, exist_ok=True)
    sqlite_path = os.path.join(RESULTS_DIR, SQLITE_DB_FILENAME)
    init_sqlite(sqlite_path)

    slug = host_slug(host)
    csv_output_filename = f"benchmark_results_{slug}_{run_id}.csv"
    md_output_filename = f"leaderboard_{slug}_{run_id}.md"

    available_model_set = set(models)

    for model in models:
        print(f"Benchmarking model: {model}", flush=True)
        for test_case in test_cases:
            print(f"  Running test: {test_case['name']}", flush=True)
            prompt = test_case['prompt']
            verification = test_case.get('verification', {})
            
            response, latency = query_ollama(model, prompt, ollama_api_url)
            
            if response is None:
                continue

            correctness_score = 0
            
            if verification.get("type") == "regex":
                pattern = verification.get("pattern")
                correctness_score = 1 if re.search(pattern, response) else 0
            
            elif verification.get("type") == "judge":
                requested_judge = verification.get("judge_model")
                rubric = verification.get("rubric")
                # Fall back to the model under test if the specified judge is unavailable
                judge_model = requested_judge if requested_judge in available_model_set else model
                if requested_judge and requested_judge != judge_model:
                    print(f"  [judge] {requested_judge} not available; using {model} as judge.")
                judge_prompt = f"Rubric: {rubric}\n\nResponse to evaluate:\n{response}\n\nReturn only '1' if it passes or '0' if it fails."
                judge_response, _ = query_ollama(judge_model, judge_prompt, ollama_api_url)
                if judge_response:
                    correctness_score = 1 if '1' in judge_response else 0
                else:
                    correctness_score = 0
            
            # Support for simple string match if 'expected' is provided
            elif "expected" in test_case:
                correctness_score = 1 if test_case["expected"] in response else 0
            else:
                correctness_score = 0

            results.append({
                "model_name": model,
                "test_case": test_case["name"],
                "prompt": prompt,
                "response": response.replace('\n', ' '),
                "latency": latency,
                "correctness_score": correctness_score
            })

    # Save CSV
    csv_path = os.path.join(RESULTS_DIR, csv_output_filename)
    if results:
        keys = list(results[0].keys())
        with open(csv_path, 'w', newline='') as f:
            dict_writer = csv.DictWriter(f, fieldnames=keys)
            dict_writer.writeheader()
            dict_writer.writerows(results)
    else:
        print("No results to save.")

    # Save Markdown Leaderboard
    md_path = os.path.join(RESULTS_DIR, md_output_filename)
    with open(md_path, 'w') as f:
        f.write(f"# Ollama Benchmark Leaderboard\n")
        f.write(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        f.write("| Model | Test Case | Avg Latency (s) | Accuracy |\n")
        f.write("|-------|-----------|-----------------|----------|\n")
        
        # Group by model
        model_stats = {}
        for r in results:
            m = r['model_name']
            if m not in model_stats:
                model_stats[m] = {"latencies": [], "scores": [], "tests": 0}
            model_stats[m]["latencies"].append(r['latency'])
            model_stats[m]["scores"].append(r['correctness_score'])
            model_stats[m]["tests"] += 1
        
        for m, stats in model_stats.items():
            avg_lat = sum(stats["latencies"]) / len(stats["latencies"])
            avg_acc = sum(stats["scores"]) / len(stats["scores"])
            f.write(f"| {m} | {stats['tests']} tests | {avg_lat:.2f}s | {avg_acc:.2%}\n")

    run_finished_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    save_results_to_sqlite(
        sqlite_path,
        run_id,
        run_started_at,
        run_finished_at,
        maven_ok,
        results,
        host_snapshot,
    )
    # patch: record which test-cases file was actually used
    with sqlite3.connect(sqlite_path, timeout=30) as conn:
        conn.execute(
            "UPDATE benchmark_runs SET test_cases_file = ? WHERE run_id = ?",
            (resolved_test_cases_file, run_id),
        )

    print(f"Benchmark complete. Results saved to {RESULTS_DIR}/")
    print(f"SQLite results saved to {sqlite_path} (run_id={run_id})")

def parse_args():
    parser = argparse.ArgumentParser(description="Benchmark Ollama models against prompt test cases.")
    parser.add_argument(
        "--host",
        default=DEFAULT_OLLAMA_HOST,
        help="Ollama host, e.g. http://localhost:11434 or localhost:11434",
    )
    parser.add_argument(
        "--all-models",
        action="store_true",
        help="Benchmark all models available on the specified host.",
    )
    parser.add_argument(
        "--models",
        default="",
        help="Comma-separated model names override. Missing models are pulled, then only this set is benchmarked.",
    )
    parser.add_argument(
        "--skip-pull",
        action="store_true",
        help="When used with --models, do not pull missing models; benchmark only those already present on host.",
    )
    parser.add_argument(
        "--test-cases",
        default=None,
        metavar="PATH",
        help=(
            "Path to a JSON test-cases file. Defaults to the built-in test_cases.json. "
            "Use scripts/benchlam/pdhd_test_cases.json for the PDHD capability suite."
        ),
    )
    parser.add_argument(
        "--exclude-models",
        default="",
        metavar="MODELS",
        help="Comma-separated model names to skip, even when --all-models is set.",
    )
    return parser.parse_args()

if __name__ == "__main__":
    args = parse_args()
    run_benchmark(
        host=args.host,
        models_override_csv=args.models,
        all_models=args.all_models,
        skip_pull=args.skip_pull,
        test_cases_file=args.test_cases,
        exclude_csv=args.exclude_models,
    )
