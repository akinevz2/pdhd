 -- SQL Transformation: From Raw Logs to Engineering Metrics
    -- Purpose: Filter, clean, and aggregate host response times for visualization.

    WITH cleaned_logs AS (
        SELECT
            host_id,
            datetime(timestamp, 'unixepoch') AS event_time,
            -- Extracting duration from the log entry (assumes duration is stored as an integer)
            CAST(duration_ms AS FLOAT) AS latency_ms,
            status_code
        FROM raw_logs
        WHERE status_code = 200  -- We only care about successful requests for latency analysis
          AND duration_ms IS NOT NULL
    )
    SELECT
        host_id,
        event_time,
        latency_desc AS latency_ms,
        -- We create a 'bin' to help with boxplot grouping if needed
        strftime('%H', event_time) AS hour_of_day
    FROM cleaned_logs
    ORDER BY event_time ASC;