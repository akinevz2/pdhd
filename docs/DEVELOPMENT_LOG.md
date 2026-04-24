# Project Development Lore (2026-03-30 to 2026-04-15)

### Phase 1: Ergonomics & Schema Standardization (Late March)

- **Focus:** Reducing "magic strings" and technical debt in the payload layer.
- **Key Work:** Centralized all signal keys (path, metadata, search) into a `ToolSupport` and `SchemaKeys` architecture. This made API and UI communication more predictable and easier to extend safely.

### Phase 2: Reliability & Startup Optimization (Early April)

- **Focus:** Strengthening fail-fast behavior and simplifying Ollama integration.
- **Key Work:**
  - Removed fragile startup logic that used Testcontainers or auto-pulled models.
  - Implemented a health-check-first approach so the app validates Ollama endpoint reachability before core services start.
  - Improved CLI/menu error propagation to avoid silent failures.

### Phase 3: Feature Expansion & Observability (Mid April)

- **Focus:** Implementing technical recommendations and expanding telemetry/benchmark evidence.
- **Key Work:**
  - **Telemetry:** Added `tool_telemetry` and `model_call_telemetry` tracking for runtime behavior analysis.
  - **Benchmarking:** Standardized on `scripts/benchlam/benchmark_ollama.py` with SQLite-backed output artifacts for report-ready analysis.
  - **Standardization:** Extended the support-class pattern across the codebase to improve consistency in workspace, file-detection, and tool behavior.
