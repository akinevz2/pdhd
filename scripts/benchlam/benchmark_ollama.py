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
DEFAULT_OLLAMA_HOST = "http://host.docker.internal:11434"
DEFAULT_PDHD_BASE_URL = "http://localhost:8080"
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
        conn.execute("""
            CREATE TABLE IF NOT EXISTS benchmark_telemetry_snapshot (
                run_id TEXT PRIMARY KEY,
                captured_at TEXT NOT NULL,
                total_invocations INTEGER NOT NULL,
                total_failures INTEGER NOT NULL,
                tool_failure_rate REAL NOT NULL,
                arg_validation_failure_rate REAL NOT NULL,
                payload TEXT,
                FOREIGN KEY(run_id) REFERENCES benchmark_runs(run_id)
            )
        """)
        # Additive migration: add new columns to benchmark_results if absent
        for _col, _spec in [("http_status", "INTEGER DEFAULT 0"), ("error_kind", "TEXT DEFAULT ''")]:
            try:
                conn.execute(f"ALTER TABLE benchmark_results ADD COLUMN {_col} {_spec}")
            except sqlite3.OperationalError:
                pass  # column already present
        # Additive migration: add P50/P95 columns to benchmark_model_summary if absent
        for _col, _spec in [("p50_latency", "REAL DEFAULT 0"), ("p95_latency", "REAL DEFAULT 0"), ("http_error_rate", "REAL DEFAULT 0")]:
            try:
                conn.execute(f"ALTER TABLE benchmark_model_summary ADD COLUMN {_col} {_spec}")
            except sqlite3.OperationalError:
                pass  # column already present


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
                    (run_id, model_name, test_case, prompt, response, latency, correctness_score, http_status, error_kind)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                        int(r.get("http_status", 0)),
                        r.get("error_kind", ""),
                    )
                    for r in results
                ],
            )

            model_stats = {}
            for r in results:
                model_name = r["model_name"]
                if model_name not in model_stats:
                    model_stats[model_name] = {"latencies": [], "scores": [], "http_errors": 0}
                model_stats[model_name]["latencies"].append(float(r["latency"]))
                model_stats[model_name]["scores"].append(int(r["correctness_score"]))
                if int(r.get("http_status", 0)) >= 400:
                    model_stats[model_name]["http_errors"] += 1

            def _pct(sorted_vals, p):
                if not sorted_vals:
                    return 0.0
                idx = max(0, int(len(sorted_vals) * p / 100) - 1)
                return sorted(sorted_vals)[idx]

            conn.executemany(
                """
                INSERT INTO benchmark_model_summary
                    (run_id, model_name, tests_run, avg_latency, avg_accuracy, p50_latency, p95_latency, http_error_rate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                [
                    (
                        run_id,
                        model_name,
                        len(stats["latencies"]),
                        sum(stats["latencies"]) / len(stats["latencies"]),
                        sum(stats["scores"]) / len(stats["scores"]),
                        _pct(stats["latencies"], 50),
                        _pct(stats["latencies"], 95),
                        stats["http_errors"] / len(stats["latencies"]) if stats["latencies"] else 0.0,
                    )
                    for model_name, stats in model_stats.items()
                ],
            )

def normalize_ollama_host(host):
    """Normalizes host values like host.docker.internal:11434 to http://host.docker.internal:11434."""
    normalized = host.strip()
    if not normalized.startswith("http://") and not normalized.startswith("https://"):
        normalized = f"http://{normalized}"
    return normalized.rstrip("/")


def normalize_pdhd_base_url(base_url):
    """Normalizes PDHD base URLs like localhost:8080 to http://localhost:8080."""
    normalized = base_url.strip()
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


def make_pdhd_chat_url(base_url):
    """Builds the PDHD chat endpoint URL for benchmark prompts."""
    return f"{normalize_pdhd_base_url(base_url)}/api/chat"


def make_pdhd_menu_url(base_url, operation):
    """Builds PDHD menu operation URLs."""
    normalized = normalize_pdhd_base_url(base_url)
    op = operation if operation.startswith("/") else f"/{operation}"
    return f"{normalized}/api/menu{op}"


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


def query_pdhd(prompt, pdhd_chat_url):
    """Queries the PDHD chat API for a given prompt.

    Returns (response_text, latency, http_status, error_kind).
    On HTTP error responses the error_kind is extracted from the JSON body
    when the server returns {"errorKind": "...", "detail": "..."} (e.g. 502
    AI_LAYER_FAILURE or 500 SERVICE_LAYER_FAILURE from ExceptionMappers).
    response_text is None on transport-level errors.
    """
    payload = {
        "message": prompt,
    }
    headers = {
        "Content-Type": "application/json",
        "Accept": "text/plain",
    }
    start_time = time.time()
    try:
        response = requests.post(pdhd_chat_url, json=payload, headers=headers, timeout=180)
        latency = time.time() - start_time
        if response.status_code >= 400:
            error_kind = ""
            try:
                body = response.json()
                error_kind = body.get("errorKind", "") or ""
            except Exception:
                pass
            print(f"[pdhd] HTTP {response.status_code} from chat API; errorKind={error_kind or '<none>'}")
            return None, latency, response.status_code, error_kind
        return response.text, latency, response.status_code, ""
    except Exception as e:
        latency = time.time() - start_time
        print(f"Error querying PDHD chat API: {e}")
        return None, latency, 0, ""


def configure_pdhd_ollama_runtime(pdhd_base_url, ollama_host):
    """Configures PDHD runtime provider to use the requested external Ollama host."""
    if not ollama_host:
        return True

    runtime_url = make_pdhd_menu_url(pdhd_base_url, "/ollama/runtime/provider")
    payload = {
        "provider": "EXTERNAL",
        "baseUrl": normalize_ollama_host(ollama_host),
    }

    try:
        response = requests.post(runtime_url, json=payload, timeout=20)
        response.raise_for_status()
        print(f"[pdhd] Runtime Ollama host set to {payload['baseUrl']}", flush=True)
        return True
    except Exception as e:
        print(f"[pdhd] Failed to set runtime Ollama host via {runtime_url}: {e}", flush=True)
        return False


def get_pdhd_ollama_configuration(pdhd_base_url):
    """Returns PDHD Ollama configuration payload (best-effort)."""
    url = make_pdhd_menu_url(pdhd_base_url, "/ollama")
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        payload = response.json()
        return payload if isinstance(payload, dict) else {}
    except Exception as exc:
        print(f"[pdhd] Could not read runtime model configuration from {url}: {exc}", flush=True)
        return {}


def configure_pdhd_model(pdhd_base_url, model_name, base_url=None):
    """Sets PDHD runtime model name (and optional base URL) before running prompts."""
    if not model_name:
        return False

    settings = {
        "modelName": model_name,
    }
    if base_url:
        settings["baseUrl"] = normalize_ollama_host(base_url)

    payload = {
        "settings": settings,
    }
    config_url = make_pdhd_menu_url(pdhd_base_url, "/ollama")
    try:
        response = requests.post(config_url, json=payload, timeout=20)
        response.raise_for_status()
        print(f"[pdhd] Runtime model set to {model_name}", flush=True)
        return True
    except Exception as exc:
        print(f"[pdhd] Failed to set runtime model via {config_url}: {exc}", flush=True)
        return False


def reset_pdhd_chat_memory(pdhd_base_url):
    """Best-effort reset of PDHD chat memory before benchmark run."""
    url = make_pdhd_chat_url(pdhd_base_url)
    try:
        response = requests.delete(url, timeout=10)
        if response.status_code < 300:
            print("[pdhd] Cleared chat memory before benchmark run.", flush=True)
    except Exception:
        # Non-fatal; benchmark can proceed without reset.
        pass


def fetch_pdhd_telemetry_snapshot(pdhd_base_url):
    """Fetches /api/telemetry from PDHD and returns a structured snapshot dict.

    Returns a dict ready for INSERT into benchmark_telemetry_snapshot, or None
    if the endpoint is not reachable.
    """
    url = f"{normalize_pdhd_base_url(pdhd_base_url)}/api/telemetry"
    payload = safe_get_json(url, timeout_seconds=10)
    if payload is None:
        print(f"[pdhd] Could not fetch telemetry from {url}", flush=True)
        return None

    items = payload.get("items") or []
    total_invocations = sum(int(i.get("invocations", 0)) for i in items)
    total_failures = sum(int(i.get("failures", 0)) for i in items)
    total_arg_failures = sum(int(i.get("argumentValidationFailures", 0)) for i in items)
    tool_failure_rate = total_failures / total_invocations if total_invocations else 0.0
    arg_failure_rate = total_arg_failures / total_invocations if total_invocations else 0.0

    return {
        "captured_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "total_invocations": total_invocations,
        "total_failures": total_failures,
        "tool_failure_rate": tool_failure_rate,
        "arg_validation_failure_rate": arg_failure_rate,
        "payload": json.dumps(payload),
    }


def save_telemetry_snapshot(db_path, run_id, snapshot):
    """Persists a PDHD telemetry snapshot to SQLite."""
    if snapshot is None:
        return
    with sqlite3.connect(db_path, timeout=30) as conn:
        conn.execute(
            """
            INSERT OR REPLACE INTO benchmark_telemetry_snapshot
                (run_id, captured_at, total_invocations, total_failures,
                 tool_failure_rate, arg_validation_failure_rate, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                run_id,
                snapshot["captured_at"],
                int(snapshot["total_invocations"]),
                int(snapshot["total_failures"]),
                float(snapshot["tool_failure_rate"]),
                float(snapshot["arg_validation_failure_rate"]),
                snapshot["payload"],
            ),
        )


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


def probe_pdhd_snapshot(base_url):
    """Collects PDHD host metadata for SQLite logging using existing schema columns."""
    normalized_base_url = normalize_pdhd_base_url(base_url)
    resolved_host = extract_host_for_resolution(normalized_base_url)
    resolved_ip = ""
    try:
        resolved_ip = socket.gethostbyname(resolved_host)
    except Exception:
        resolved_ip = ""

    api_reachable = False
    try:
        health = requests.get(f"{normalized_base_url}/q/health", timeout=6)
        api_reachable = health.status_code < 500
    except Exception:
        api_reachable = False

    return {
        "ollama_host": normalized_base_url,
        "resolved_host": resolved_host,
        "resolved_ip": resolved_ip,
        "api_reachable": api_reachable,
        "ollama_version": "",
        "available_models_count": 0,
        "running_models_count": 0,
        "total_running_size_vram_bytes": 0,
        "max_running_size_vram_bytes": 0,
        "tags_payload": "",
        "ps_payload": "",
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


def resolve_pdhd_target_models(pdhd_base_url, destination_ollama_host, models_override_csv, all_models, exclude_csv=None):
    """Resolves PDHD benchmark model list from models known on the destination host."""
    available = set(get_available_models(destination_ollama_host))
    excluded = {m.strip() for m in (exclude_csv or "").split(",") if m.strip()}

    if not available:
        print(f"[benchmark] No models discovered on destination host {normalize_ollama_host(destination_ollama_host)}")
        return []

    if excluded:
        print(f"[models] Excluding: {', '.join(sorted(excluded))}")

    requested = [m.strip() for m in (models_override_csv or "").split(",") if m.strip()]
    if requested:
        missing = [m for m in requested if m not in available]
        if missing:
            print(
                f"[benchmark] Requested PDHD model(s) missing on destination {normalize_ollama_host(destination_ollama_host)}: "
                f"{', '.join(missing)}"
            )
            print(f"[benchmark] Available models: {', '.join(sorted(available))}")
            return []
        return [m for m in requested if m not in excluded]

    selectable = sorted(available - excluded)
    if all_models:
        return selectable

    config_payload = get_pdhd_ollama_configuration(pdhd_base_url)
    configured_model = (config_payload.get("modelName") or "").strip()
    if configured_model and configured_model in selectable:
        return [configured_model]

    preferred_defaults = ["llama3.1:latest", "qwen3.6:latest", "gemma4:latest"]
    for candidate in preferred_defaults:
        if candidate in selectable:
            print(f"[benchmark] PDHD configured model '{configured_model or 'unset'}' is unavailable; using {candidate}.")
            return [candidate]

    fallback = selectable[0] if selectable else ""
    if fallback:
        print(f"[benchmark] PDHD configured model '{configured_model or 'unset'}' is unavailable; using {fallback}.")
        return [fallback]
    return []


def preflight_judge_models(test_cases, judge_host, judge_fallback_model=None):
    """Ensures every judge model exists before running tests.

    When a requested judge model is absent and fallback exists, rewrites that
    test case to use the fallback model.
    """
    available_judges = set(get_available_models(judge_host))
    if not available_judges:
        print(f"[judge] No models discovered on judge host {normalize_ollama_host(judge_host)}")
        return False

    if judge_fallback_model and judge_fallback_model not in available_judges:
        print(
            f"[judge] Fallback judge model '{judge_fallback_model}' is not available on "
            f"{normalize_ollama_host(judge_host)}"
        )
        print(f"[judge] Available models: {', '.join(sorted(available_judges))}")
        return False

    missing = set()
    rewrites = 0
    for test_case in test_cases:
        verification = test_case.get("verification", {})
        if verification.get("type") != "judge":
            continue

        requested = (verification.get("judge_model") or "").strip()
        if requested in available_judges:
            continue

        if judge_fallback_model:
            verification["judge_model"] = judge_fallback_model
            rewrites += 1
            print(
                f"[judge] {requested or '<unset>'} not available on {normalize_ollama_host(judge_host)}; "
                f"using fallback {judge_fallback_model} for test '{test_case.get('name', '<unnamed>')}'."
            )
            continue

        missing.add(requested or "<unset>")

    if missing:
        print(f"[judge] Missing judge model(s) on {normalize_ollama_host(judge_host)}: {', '.join(sorted(missing))}")
        print(f"[judge] Available models: {', '.join(sorted(available_judges))}")
        print("[judge] Aborting benchmark. Provide --judge-host and/or --judge-fallback-model.")
        return False

    if rewrites:
        print(f"[judge] Applied fallback judge model to {rewrites} test(s).")
    return True


def run_benchmark(
    host,
    models_override_csv=None,
    all_models=False,
    skip_pull=False,
    test_cases_file=None,
    exclude_csv=None,
    target="ollama",
    pdhd_base_url=None,
    pdhd_ollama_host=None,
    judge_host=None,
    judge_fallback_model=None,
    repeat=1,
):
    run_started_at = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    run_id = datetime.now().strftime('%Y%m%d_%H%M%S') + "_" + uuid.uuid4().hex[:8]
    ollama_api_url = make_ollama_api_url(host)
    pdhd_chat_url = make_pdhd_chat_url(pdhd_base_url or DEFAULT_PDHD_BASE_URL)
    host_snapshot = probe_host_snapshot(host) if target == "ollama" else probe_pdhd_snapshot(pdhd_base_url or DEFAULT_PDHD_BASE_URL)

    resolved_test_cases_file = test_cases_file or DEFAULT_TEST_CASES_FILE

    with open(resolved_test_cases_file, 'r') as f:
        test_cases = json.load(f)

    resolved_judge_host = judge_host
    if not resolved_judge_host:
        resolved_judge_host = "http://host.docker.internal:11434" if target == "pdhd" else host
    resolved_judge_host = normalize_ollama_host(resolved_judge_host)

    if not preflight_judge_models(test_cases, resolved_judge_host, judge_fallback_model=judge_fallback_model):
        return

    judge_api_url = make_ollama_api_url(resolved_judge_host)

    if target == "ollama":
        models = resolve_target_models(host, models_override_csv, all_models, skip_pull=skip_pull, exclude_csv=exclude_csv)
    else:
        if not configure_pdhd_ollama_runtime(pdhd_base_url or DEFAULT_PDHD_BASE_URL, pdhd_ollama_host):
            print("[benchmark] Aborting: PDHD runtime Ollama host could not be configured.")
            return
        reset_pdhd_chat_memory(pdhd_base_url or DEFAULT_PDHD_BASE_URL)
        if skip_pull:
            print("[benchmark] --skip-pull is only relevant in --target ollama mode; ignoring.")
        destination_host = pdhd_ollama_host or host
        models = resolve_pdhd_target_models(
            pdhd_base_url or DEFAULT_PDHD_BASE_URL,
            destination_host,
            models_override_csv,
            all_models,
            exclude_csv=exclude_csv,
        )

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

    results = []
    
    os.makedirs(RESULTS_DIR, exist_ok=True)
    sqlite_path = os.path.join(RESULTS_DIR, SQLITE_DB_FILENAME)
    init_sqlite(sqlite_path)

    slug = host_slug(host)
    csv_output_filename = f"benchmark_results_{slug}_{run_id}.csv"
    md_output_filename = f"leaderboard_{slug}_{run_id}.md"

    for model in models:
        if target == "pdhd":
            if not configure_pdhd_model(pdhd_base_url or DEFAULT_PDHD_BASE_URL, model, base_url=pdhd_ollama_host):
                print(f"[benchmark] Skipping model {model}: unable to configure PDHD runtime model.")
                continue
        print(f"Benchmarking model: {model}", flush=True)
        for test_case in test_cases:
            print(f"  Running test: {test_case['name']}", flush=True)
            prompt = test_case['prompt']
            verification = test_case.get('verification', {})

            run_count = max(1, repeat)
            for rep in range(run_count):
                if run_count > 1:
                    print(f"    repeat {rep + 1}/{run_count}", flush=True)

                http_status = 0
                error_kind = ""
                if target == "ollama":
                    response, latency = query_ollama(model, prompt, ollama_api_url)
                else:
                    response, latency, http_status, error_kind = query_pdhd(prompt, pdhd_chat_url)

                correctness_score = 0

                if response is not None:
                    if verification.get("type") == "regex":
                        pattern = verification.get("pattern")
                        correctness_score = 1 if re.search(pattern, response) else 0

                    elif verification.get("type") == "judge":
                        judge_model = verification.get("judge_model")
                        rubric = verification.get("rubric")
                        judge_prompt = f"Rubric: {rubric}\n\nResponse to evaluate:\n{response}\n\nReturn only '1' if it passes or '0' if it fails."
                        judge_response, _ = query_ollama(judge_model, judge_prompt, judge_api_url)
                        if judge_response:
                            correctness_score = 1 if '1' in judge_response else 0
                        else:
                            correctness_score = 0

                    elif "expected" in test_case:
                        correctness_score = 1 if test_case["expected"] in response else 0

                results.append({
                    "model_name": model,
                    "test_case": test_case["name"],
                    "prompt": prompt,
                    "response": response.replace('\n', ' ') if response else "",
                    "latency": latency,
                    "correctness_score": correctness_score,
                    "http_status": http_status,
                    "error_kind": error_kind,
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
        f.write(f"# Benchmark Leaderboard\n")
        f.write(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"Target: {target}  Repeat: {repeat}\n\n")
        f.write("| Model | Tests | Avg Latency (s) | P50 (s) | P95 (s) | Accuracy | HTTP Error Rate |\n")
        f.write("|-------|-------|-----------------|---------|---------|----------|-----------------|\n")

        model_stats = {}
        for r in results:
            m = r['model_name']
            if m not in model_stats:
                model_stats[m] = {"latencies": [], "scores": [], "http_errors": 0}
            model_stats[m]["latencies"].append(r['latency'])
            model_stats[m]["scores"].append(r['correctness_score'])
            if int(r.get("http_status", 0)) >= 400:
                model_stats[m]["http_errors"] += 1

        def _pct_md(vals, p):
            if not vals:
                return 0.0
            return sorted(vals)[max(0, int(len(vals) * p / 100) - 1)]

        for m, stats in model_stats.items():
            lats = stats["latencies"]
            n = len(lats)
            avg_lat = sum(lats) / n if n else 0.0
            p50 = _pct_md(lats, 50)
            p95 = _pct_md(lats, 95)
            avg_acc = sum(stats["scores"]) / n if n else 0.0
            http_err_rate = stats["http_errors"] / n if n else 0.0
            f.write(f"| {m} | {n} | {avg_lat:.2f} | {p50:.2f} | {p95:.2f} | {avg_acc:.2%} | {http_err_rate:.2%} |\n")

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

    # Capture PDHD telemetry snapshot after run (PDHD target only)
    if target == "pdhd":
        telemetry_snapshot = fetch_pdhd_telemetry_snapshot(pdhd_base_url or DEFAULT_PDHD_BASE_URL)
        save_telemetry_snapshot(sqlite_path, run_id, telemetry_snapshot)
        if telemetry_snapshot:
            print(
                f"[telemetry] tool_failure_rate={telemetry_snapshot['tool_failure_rate']:.2%}  "
                f"arg_validation_failure_rate={telemetry_snapshot['arg_validation_failure_rate']:.2%}  "
                f"total_invocations={telemetry_snapshot['total_invocations']}",
                flush=True,
            )

    print(f"Benchmark complete. Results saved to {RESULTS_DIR}/")
    print(f"SQLite results saved to {sqlite_path} (run_id={run_id})")

def parse_args():
    parser = argparse.ArgumentParser(description="Benchmark Ollama models against prompt test cases.")
    parser.add_argument(
        "--target",
        choices=["ollama", "pdhd"],
        default="ollama",
        help="Benchmark target type: direct Ollama API or PDHD /api/chat endpoint.",
    )
    parser.add_argument(
        "--host",
        default=DEFAULT_OLLAMA_HOST,
        help="Ollama host, e.g. http://host.docker.internal:11434 or host.docker.internal:11434",
    )
    parser.add_argument(
        "--pdhd-base-url",
        default=DEFAULT_PDHD_BASE_URL,
        help="PDHD base URL, e.g. http://localhost:8080 (used when --target pdhd).",
    )
    parser.add_argument(
        "--pdhd-ollama-host",
        default=None,
        help="Ollama host to set in PDHD runtime before benchmark prompts (used when --target pdhd).",
    )
    parser.add_argument(
        "--judge-host",
        default=None,
        help=(
            "Ollama host used for judge-model evaluations. "
            "Defaults to --host in ollama mode and host.docker.internal:11434 in pdhd mode."
        ),
    )
    parser.add_argument(
        "--judge-fallback-model",
        default="llama3.1:latest",
        help=(
            "Fallback judge model used when a test case requests a missing judge model. "
            "Set empty string to disable fallback and fail fast on missing judge models."
        ),
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
    parser.add_argument(
        "--repeat",
        type=int,
        default=1,
        metavar="N",
        help=(
            "Run each test case N times per model. "
            "Latency P50/P95 are computed across all N repetitions. Default: 1."
        ),
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
        target=args.target,
        pdhd_base_url=args.pdhd_base_url,
        pdhd_ollama_host=args.pdhd_ollama_host,
        judge_host=args.judge_host,
        judge_fallback_model=(args.judge_fallback_model.strip() if args.judge_fallback_model else None),
        repeat=args.repeat,
    )
