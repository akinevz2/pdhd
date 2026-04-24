-- =============================================================================
-- q_latency_summary.sql
-- =============================================================================
-- Purpose : Aggregate latency statistics per (host, model, test_case).
--           Companion to Figure 1 for readers who prefer tabular statistics
--           over graphical distributions.
--
-- Columns
--   host         : Ollama API base URL
--   model        : Model tag
--   test_case    : Benchmark prompt name
--   mean_lat     : Arithmetic mean latency across all runs (seconds)
--   min_lat      : Fastest observed response (seconds)
--   max_lat      : Slowest observed response (seconds)
--   n            : Number of measurements (may be < total runs if timeouts)
-- =============================================================================

SELECT
    h.ollama_host                   AS host,
    s.model_name                    AS model,
    s.test_case                     AS test_case,
    ROUND(AVG(s.latency), 2)        AS mean_lat,   -- seconds
    ROUND(MIN(s.latency), 2)        AS min_lat,    -- seconds
    ROUND(MAX(s.latency), 2)        AS max_lat,    -- seconds
    COUNT(*)                        AS n
FROM
    benchmark_results           s
    JOIN benchmark_runs         r  ON r.run_id = s.run_id
    JOIN benchmark_host_snapshot h  ON h.run_id = s.run_id
GROUP BY
    h.ollama_host,
    s.model_name,
    s.test_case
ORDER BY
    h.ollama_host,
    mean_lat ASC;
