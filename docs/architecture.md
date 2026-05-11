# Architecture Overview

## Application Structure

The application is a Quarkus Picocli CLI tool with a browser-based web UI and assistant-backed inspection flows. It is composed of five main layers:

```
┌────────────────────────────────────────────────────────┐
│                     Entry Point                        │
│            PdhdLauncher (root Picocli command)         │
└───────────────────────┬────────────────────────────────┘
                        │ subcommands
           ┌─────────────┴──────────────┐
           ▼                            ▼
OllamaConfigCommand               WebUiCommand
           │                            │
           ▼                            ▼
ModelConfigService            Quarkus HTTP runtime
```

## End-to-End Runtime Flow

The current launcher path is:

- `PdhdLauncher.main(String[] args)` starts Quarkus directly.
- `PdhdLauncher.run(String... args)` defaults to `webui` when no arguments are provided.
- Picocli dispatch then resolves either `WebUiCommand` or `OllamaConfigCommand` through the injected CDI-aware `IFactory`.

---

## Package Map

| Package                         | Purpose                                             |
| ------------------------------- | --------------------------------------------------- |
| `ac.uk.sussex.kn253`            | Root: `PdhdLauncher` Picocli entry point            |
| `ac.uk.sussex.kn253.commands`   | Picocli subcommands: `configure`, `webui`           |
| `ac.uk.sussex.kn253.events`     | CDI event records for cwd and model-config signals  |
| `ac.uk.sussex.kn253.services`   | Application-scoped business logic and CDI producers |
| `ac.uk.sussex.kn253.ollama`     | REST client and data types for the Ollama HTTP API  |
| `ac.uk.sussex.kn253.repository` | Panache/JPA persistence models and related enums    |
| `ac.uk.sussex.kn253.resources`  | JAX-RS REST endpoints (web UI back-end)             |
| `ac.uk.sussex.kn253.tools`      | LangChain4j tool-calling implementations            |

---

The current events package is intentionally small. `CwdResolvedEvent` is fired by `CwdService` when the working directory is persisted as a `ProjectFolder`, and `ModelConfigEvent` remains the package's model-configuration event record. Earlier assistant repaint/resize event records are no longer present in the source tree.

---

## CLI Command Wiring

```
PdhdLauncher
@TopCommand
@Command(name = "pdhd", subcommands = {
    OllamaConfigCommand.class,
    WebUiCommand.class
})
          │
          ├── configure  -> OllamaConfigCommand -> ModelConfigService
          └── webui      -> WebUiCommand
```

`PdhdLauncher.run(String... args)` defaults to `webui` when no command-line arguments are provided. `PdhdLauncher.main(String[] args)` starts Quarkus directly, and `configure`, `help`, and version requests are handled through Picocli without any extra bootstrap layer.

---

## Removed Legacy Diagrams

Older revisions of this document described an `AssistantMenu`-driven terminal UI, related adapter classes, and assistant repaint/resize events. Those classes are not present in the current source tree, so those diagrams have been removed instead of being carried forward as if they still described the runtime.

For current implementation details, use the source tree and the focused feature notes under `docs/` rather than the removed terminal-menu diagrams.

---

## Menu Hierarchy (Picocli)

```
PdhdLauncher        (@Command "pdhd")
├── OllamaConfigCommand  (@Command "configure")
└── WebUiCommand         (@Command "webui")
```

Both subcommands are CDI-managed picocli commands resolved through the injected `IFactory` in `PdhdLauncher`.

---

## Services Layer

| Service                            | Scope                | Responsibility                                                     |
| ---------------------------------- | -------------------- | ------------------------------------------------------------------ |
| `TerminalLifecycleService`         | `@ApplicationScoped` | Produces `@Named("mainTerminal")` Terminal; `@PreDestroy` teardown |
| `AssistantService`                 | `@ApplicationScoped` | LangChain4j AI chat orchestration                                  |
| `ModelConfigService`               | `@ApplicationScoped` | Reads/writes model configuration (Panache)                         |
| `OllamaManagementService`          | `@ApplicationScoped` | Ollama REST operations (list, pull, delete, etc.)                  |
| `TelemetryService`                 | `@ApplicationScoped` | Records model call metrics                                         |
| `AssistantWorkingDirectoryService` | `@ApplicationScoped` | Tracks current working directory for context                       |
| `CwdService`                       | (scoped)             | Shared cwd state for REST + CLI layers                             |
| `WebUiService`                     | `@ApplicationScoped` | Manages embedded web UI lifecycle                                  |
| `RuntimeManagementService`         | `@ApplicationScoped` | Application shutdown utilities                                     |
