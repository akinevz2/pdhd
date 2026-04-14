# Evaluation Environment Specification

**Date:** 2026-04-14  
**Related benchmark:** [benchmark-scenarios.md](benchmark-scenarios.md)

---

## Purpose

This file pins the environment variables, model configuration, and runtime
settings in effect when a benchmark run is recorded. A new copy should be
committed (or appended to this file) for each meaningful run.

---

## Template: fill in for each run

```
Run date/time (ISO-8601):
Host OS:
CPU model:
RAM (GB):
Java version:
Quarkus version:
LangChain4j version:
Model name:
Model variant/tag:
Model context window:
Model temperature:
Ollama version:
Ollama host:
pdhd.ollama.base-url:
pdhd.ollama.model-name:
Benchmark script version:
Results file:
```

---

## Baseline run – 2026-04-14 (initial)

```
Run date/time (ISO-8601): 2026-04-14T00:00:00Z
Host OS:                  Debian GNU/Linux 12 (bookworm) in dev-container
CPU model:                (record at run time)
RAM (GB):                 (record at run time)
Java version:             21+
Quarkus version:          see pom.xml
LangChain4j version:      see pom.xml
Model name:               gemma4
Model variant/tag:        gemma4:latest
Model context window:     128k (default)
Model temperature:        0.5
Ollama version:           (record at run time)
Ollama host:              host.docker.internal:11434
pdhd.ollama.base-url:     http://host.docker.internal:11434
pdhd.ollama.model-name:   gemma4:latest
Benchmark script version: scripts/benchmark.sh (initial)
Results file:             docs/evaluation/results/ (to be generated)
```

---

## Versioning rules

1. Increment the environment spec whenever the model, temperature, or Quarkus
   version changes between runs.
2. Store the spec snapshot alongside the results JSON file in
   `docs/evaluation/results/`.
3. Record hardware profile at run time – do not leave blank fields before
   archiving a run.
