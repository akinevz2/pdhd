# Tool-Calling Infrastructure Refactoring

## Summary

This document describes the refactoring of the PDHD tool-calling infrastructure
carried out to simplify the architecture, reduce redundancy, and introduce
explicit operation-type annotations for analytics and observability.

---

## Before

### Class inventory

| Class | Role |
|---|---|
| `ToolMacro` | Interface — single tool contract |
| `ToolMacroDefinition` | Record — name, description, signals, keyphrases |
| `ToolMacros` | Static catalogue of 27 `ToolMacroDefinition` constants |
| `ToolMacroRegistry` | Routes `execute()` calls to the right `ToolMacro` |
| `ToolMacroToolset` | **Abstract** base — implements `ToolModule + ToolProvider + ToolExecutor`; wraps a registry |
| `ExploreToolset` | Concrete CDI bean — 13 explore/filesystem tools |
| `ReadToolset` | Concrete CDI bean — 1 read tool |
| `WriteToolset` | Concrete CDI bean — 6 write tools |
| `IntrospectToolset` | Concrete CDI bean — 5–7 introspect tools |
| `ToolModule` | Interface — `toolSpecifications()`, `canHandle()`, `execute()` |
| `ToolService` | CDI aggregator — injects all `ToolModule` instances, routes calls, records telemetry |

### Problems

1. **Four CDI beans for a single responsibility.** Each toolset was a thin
   subclass of `ToolMacroToolset` whose sole job was to pass a hard-coded list of
   `ToolMacro` objects to the parent constructor. None contained any logic.

2. **Tool instantiation was scattered.** To know which tools existed and how they
   were wired, you had to read four separate files in `services/tools/`.

3. **`ToolMacroToolset` implemented two unused LangChain4j interfaces.**
   `ToolProvider` and `ToolExecutor` were declared but never consumed outside the
   class hierarchy, adding dead surface area.

4. **Module-precedence list hardcoded class names.** `ToolService` contained a
   `MODULE_PRECEDENCE` list that referred to the four toolset classes by fully-
   qualified name, creating a brittle coupling.

5. **No operation category on tool definitions.** Telemetry recorded the toolset
   class name (e.g. `"ReadToolset"`) as the module label, conflating class names
   with semantic operation categories. Adding a new tool required knowing which
   class to add it to in order to get the right label.

### Wiring path (before)

```
ToolService
  └── Instance<ToolModule>  (CDI injection)
        ├── ExploreToolset  ──►  ToolMacroToolset  ──►  ToolMacroRegistry  ──►  13 tools
        ├── ReadToolset     ──►  ToolMacroToolset  ──►  ToolMacroRegistry  ──►   1 tool
        ├── WriteToolset    ──►  ToolMacroToolset  ──►  ToolMacroRegistry  ──►   6 tools
        └── IntrospectToolset ─► ToolMacroToolset  ──►  ToolMacroRegistry  ──►  5–7 tools
```

---

## After

### Class inventory

| Class | Role |
|---|---|
| `ToolMacro` | Interface — single tool contract *(unchanged)* |
| `ToolOperationType` | **New** enum — `EXPLORE`, `READ`, `WRITE`, `INTROSPECT` |
| `ToolMacroDefinition` | Record — now includes `operationType` field |
| `ToolMacros` | Static catalogue — all 27 constants now carry `ToolOperationType` |
| `ToolMacroRegistry` | Routes calls; new `operationType(toolName)` method |
| `MacroToolModule` | **New** single CDI bean — owns and instantiates all tools |
| `ToolModule` | Interface — new `default operationCategoryFor(toolName)` method |
| `ToolService` | CDI aggregator — simplified; uses `operationCategoryFor` for telemetry |

### Removed

- `ToolMacroToolset` (abstract base class)
- `ExploreToolset`
- `ReadToolset`
- `WriteToolset`
- `IntrospectToolset`

### Wiring path (after)

```
ToolService
  └── Instance<ToolModule>  (CDI injection)
        └── MacroToolModule  ──►  ToolMacroRegistry  ──►  25–27 tools
```

### Operation-type annotations

Every `ToolMacroDefinition` now carries a `ToolOperationType`:

| Operation type | Tools |
|---|---|
| `EXPLORE` | `get_current_working_directory`, `change_working_directory`, `resolve_path`, `search_paths`, `get_path_info`, `list_subdirectories`, `list_files_recursive`, `analyze_path_detailed`, `summarize_path`, `list_git_projects`, `list_github_projects`, `list_project_entries`, `get_git_log` |
| `READ` | `read_file` |
| `WRITE` | `write_file`, `create_report`, `create_timeline`, `create_plan`, `append_project_todo`, `cache_project_knowledge` |
| `INTROSPECT` | `read_folder_manifest`, `read_project_manifest`, `read_project_knowledge`, `get_session_context`, `open_workspace_canvas`, `get_embedding_context`\*, `get_recent_embeddings`\* |

\* optional — present only when `EmbeddingService` is available.

`ToolService` now records the operation-type name (e.g. `"EXPLORE"`) rather than
the toolset class name for the module label in telemetry.

---

## Impact on tests

| Old test class | New test class | Change |
|---|---|---|
| `ExploreToolsetTest` | `MacroToolModuleExploreTest` | CDI bean type updated; test logic unchanged |
| `ExploreToolsetNoCdiTest` | `MacroToolModuleNoCdiTest` | Replaced `new ExploreToolset()` → `new MacroToolModule()` |
| `IntrospectToolsetTest` | `MacroToolModuleIntrospectTest` | Replaced `new IntrospectToolset(cwd, null)` → `new MacroToolModule(cwd)` |
| `ReadToolsetTest` | `MacroToolModuleReadTest` | Replaced `new ReadToolset()` → `new MacroToolModule()` |
| `WriteToolsetTest` | `MacroToolModuleWriteTest` | Replaced `new WriteToolset()` → `new MacroToolModule()` |
| `ToolsetContractTest` | `ToolsetContractTest` | Four-toolset list → single `new MacroToolModule()` |
| `SystemPromptBuilderTest` | *(same)* | Removed three toolset instantiations |
| `OllamaToolRequestIntegrationTest` | *(same)* | Replaced three-toolset lists with single `MacroToolModule` |
| `ProjectApiResourceTest` | *(same)* | Telemetry label `"ReadToolset"` → `"READ"` |

---

## Benefits

- **Single source of truth.** All tool instantiation lives in `MacroToolModule.buildTools()`.
- **Operation category is data.** `ToolOperationType` is part of the tool's definition, not an
  accident of which class it was registered in.
- **Telemetry is semantically meaningful.** Dashboards now group by `EXPLORE / READ / WRITE /
  INTROSPECT` instead of arbitrary class names.
- **Simpler CDI graph.** One `@ApplicationScoped` bean instead of four; no abstract base.
- **Dead code removed.** `ToolMacroToolset` and its unused `ToolProvider`/`ToolExecutor`
  implementations are gone.
