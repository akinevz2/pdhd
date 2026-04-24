-- =============================================================================
-- q_individual_results.sql
-- =============================================================================
-- Purpose : Retrieve every individual benchmark measurement.
--           One row = one model answering one test-case prompt in one run.
--           This is the raw dataset used for Figures 1 and 2 (latency plots).
--
-- Columns
--   host         : Ollama API base URL (e.g. http://minifridge:11434)
--   model        : Model tag (e.g. gemma4:latest)
--   test_case    : Name of the benchmark prompt (e.g. "Capital of France")
--   latency      : Wall-clock seconds from request dispatch to full response
--   score        : 1 = correct / passed judge, 0 = incorrect / failed judge
-- =============================================================================

SELECT
    h.ollama_host           AS host,
    s.model_name            AS model,
    s.test_case             AS test_case,
    s.latency               AS latency,       -- seconds
    s.correctness_score     AS score          -- 1 or 0
FROM
    benchmark_results           s
    JOIN benchmark_runs         r  ON r.run_id = s.run_id
    JOIN benchmark_host_snapshot h  ON h.run_id = s.run_id
ORDER BY
    h.ollama_host,
    s.model_name,
    s.test_case;
