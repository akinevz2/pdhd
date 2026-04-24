-- =============================================================================
-- q_accuracy_summary.sql
-- =============================================================================
-- Purpose : Aggregate correctness scores into a pass-rate percentage.
--           One row = one (host, model, test_case) cell in the heatmap.
--           This is the dataset used for Figure 3 (accuracy heatmap).
--
-- Columns
--   host          : Ollama API base URL
--   model         : Model tag
--   test_case     : Benchmark prompt name
--   pass_rate_pct : Percentage of runs that returned a correct answer
--                   = (SUM of score=1 rows / total rows) × 100
--   n             : Total number of measurements contributing to this cell
-- =============================================================================

SELECT
    h.ollama_host                               AS host,
    s.model_name                                AS model,
    s.test_case                                 AS test_case,
    ROUND(AVG(s.correctness_score) * 100, 1)   AS pass_rate_pct,
    COUNT(*)                                    AS n
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
    s.model_name,
    s.test_case;
