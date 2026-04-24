# PDHD Project Overview

## Project Purpose

**PDHD** (Project Definition Hierarchy Discovery) is a local project-inspection system. Its primary purpose is to explore the local filesystem, document the files and folders inside a project, and build a reusable understanding of known projects and their purposes.

The assistant capability exists to support inspection, not to replace it. The highest-value workflows are discovering project roots, summarising folders, producing project-level documentation from persisted evidence, and recalling those findings later when the same project is inspected again.

## Core Capabilities

- **Filesystem Exploration**: Browse local folders, inspect files, and identify candidate project roots.
- **Project Documentation**: Generate folder summaries and project summaries as structured markdown.
- **Known Project Recall**: Persist project findings so previously inspected projects can be recalled and compared later.
- **Evidence-Grounded AI Analysis**: Use embedding-backed retrieval and RAFT-style prompting to ground summaries and next-step analysis in stored evidence.
- **Web UI and API Access**: Expose inspection workflows through browser UI and REST endpoints.

## Product Direction

### Primary Goal

PDHD should answer questions such as:

- What does this folder contain?
- What is this project for?
- Which folders matter most in this repository?
- What do we already know about this project from earlier inspection?

### Stretch Goal

The longer-term stretch goal is **AI-assisted project completion estimation**: infer how complete a project appears to be, what work streams are still open, and how much implementation risk remains, based on the evidence already collected during inspection.

## Architecture Overview

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Launcher / CLI Layer                     │
│  - PdhdLauncher (root Picocli command)                      │
│  - configure / webui subcommands                            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Runtime Bootstrap                       │
│  - PreCdiOllamaBootstrap preflight for webui/default launch │
│  - Configure/help/version bypass for recovery paths         │
└─────────────────────────────────────────────────────────────┘
                              ↓ HTTP
┌─────────────────────────────────────────────────────────────┐
│                     API Layer                               │
│  - ChatResource / MenuResource                              │
│  - WorkspaceResource / ProjectResource                      │
│  - SummaryResource / ToolTelemetryResource                  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   Service and Tool Layer                    │
│  - CwdService / ModelConfigService / TelemetryService       │
│  - OllamaManagementService / AI orchestration               │
│  - WorkspaceContextTools / ReadFileTools / WebSearchTools   │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Data Layer                              │
│  - Local filesystem                                         │
│  - Panache/JPA entities and persisted project knowledge     │
│  - Ollama-hosted chat and embedding models                  │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Patterns

1. **Inspect First**: Folder and project inspection are first-class workflows, not side effects of chat.
2. **Persisted Recall**: Generated summaries are stored so known projects can be revisited with historical context.
3. **Evidence-Grounded Analysis**: Retrieval context is supplied explicitly and RAFT-style prompting separates likely relevant evidence from distractors.
4. **Typed AI Contracts**: AI services use structured outputs where possible so higher-level services consume stable Java types instead of free-form JSON.
5. **Incremental Enrichment**: Folder summaries feed project summaries, which in turn feed later next-step or completion-oriented analysis.

## Inspection Workflow

### Folder Inspection

1. Resolve the requested path relative to the current working directory.
2. Read a folder manifest and representative files.
3. Extract a structured intermediate representation.
4. Render deterministic markdown for the folder.

### Registered Project Inspection

1. Discover the root folder and selected child folders.
2. Generate and persist folder summaries for those locations.
3. Retrieve stored semantic/graph evidence for the project.
4. Use RAFT-style analysis to produce a grounded project summary or next-step report.
5. Persist the resulting markdown so the project becomes a known, recallable project.

### Completion Estimation Roadmap

Completion estimation is not the mainline feature yet, but the current architecture is preparing for it by accumulating structured folder summaries, project summaries, retrieved evidence, and next-step analysis that can later be scored or classified into a completion estimate.

## Frontend Integration

### Tech Stack

- **React 18** with TypeScript
- **Vite 5** for build/dev tooling
- **react-markdown** + **remark-gfm** for markdown rendering
- **Three.js** (available for UI features)

### UI Components

- **App.tsx**: Main application shell and orchestration
- **ProjectWindow**: Floating explorer canvas with tree view
- **TreeView**: Recursive file tree rendering
- **ChatPanel**: Assistant interaction interface
- **FileBrowser**: Left-pane filesystem navigator

### Key Integration Points

- File tree loaded from `POST /api/project/browse`
- Text content from `POST /api/project/file`
- Images from `GET /api/project/{id}/raw?path=...`
- Chat responses rendered with markdown support
- Project knowledge displayed in explorer canvas

## Project Knowledge System

### Knowledge Structure

Project knowledge is persisted in the application database, keyed by project and logical knowledge key. The main persisted artifacts today are:

- Project summary reports.
- Project next-step reports.
- Folder summaries for selected folders inside a registered project.
- Embedding chunks and graph-derived chunks used for later semantic recall.

### Knowledge Document Format

```json
{
  "key": "summary",
  "projectDirectory": "/path/to/project",
  "generatedAt": "2026-04-08T12:00:00Z",
  "content": "# Project Summary\n\n..."
}
```

### Knowledge Lifecycle

1. Folder or project inspection generates markdown content.
2. The content is stored as a `ProjectKnowledge` row keyed by `(project, key)`.
3. Stored content is indexed into semantic embedding chunks.
4. Graph structure is extracted and indexed as additional retrieval material.
5. Later inspections can retrieve both semantic and graph-backed evidence to ground new summaries.

## Known Issues

### 1. Completion Estimation Is Not Implemented Yet

**Issue**: The system can document purpose and estimate likely next steps, but it does not yet produce a reliable completion percentage or maturity score.

**Impact**: Users still need to interpret project state manually rather than relying on a formal completion estimate.

**Recommendation**: Introduce a completion-oriented analysis pipeline grounded in persisted summaries, missing capabilities, and project signals.

### 2. Project Inspection Is Still Shallow By Default

**Issue**: Registered project inspection currently summarises the root and a bounded set of direct child folders rather than recursively mapping the whole repository.

**Impact**: Large projects may only be partially documented unless the user performs additional inspection steps.

**Recommendation**: Expand the inspection planner to select deeper folders based on project structure and retrieved evidence.

### 3. Known Project Purpose Is Stored as Markdown, Not Yet as a Typed Domain Model

**Issue**: The system stores high-value summaries, but it does not yet maintain a dedicated typed model for project purpose, scope, maturity, and completion status.

**Impact**: Higher-level reasoning across projects remains harder than it should be.

**Recommendation**: Add a structured project-profile record alongside markdown summaries.

### 4. General Chat and Inspection Workflows Are Still Loosely Coupled

**Issue**: The API exposes both generic chat and dedicated inspection endpoints, but the system still relies on the caller to choose the right path.

**Recommendation**: Add explicit task routing so inspection/documentation intents automatically prefer the project-analysis pipeline.

## Implementation Recommendations

### Priority 1: Critical Fixes

1. **Structured Project Profile**: Persist a typed record for project purpose, scope, maturity, and completion signals.
2. **Deeper Inspection Planning**: Choose important folders beyond the first directory layer.
3. **Inspection Routing**: Route documentation-oriented requests to the inspection pipeline automatically.

### Priority 2: UX Improvements

1. **Known Project Dashboard**: Show persisted purpose, latest summary, and freshness for each project.
2. **Evidence Visibility**: Surface the evidence used for project summaries and next-step estimates.
3. **Progress Signals**: Add UI indicators for inspection coverage and stale summaries.

### Priority 3: Enhancements

1. **Completion Estimation**: Use accumulated evidence to infer project completeness and remaining effort.
2. **Cross-Project Comparison**: Compare known projects by purpose, technology stack, and maturity.
3. **Historical Re-Inspection**: Track how project understanding changes over time.
4. **Typed Contracts**: Extend API responses with versioned schemas.
5. **Performance Monitoring**: Add latency tracking and optimization.
6. **Advanced Search**: Enhance path search with semantic understanding.

## Development Workflow

### Running the Application

```bash
# From repository root
./mvnw quarkus:dev
```

The backend serves the application on `http://localhost:8080`.

For frontend-only iteration:

```bash
cd src/main/webui
npm install
npm run dev
```

### Building Production Assets

```bash
cd src/main/webui
npm run build
npm run preview
```

### Testing Tool Execution

```bash
# Compile the backend
./mvnw -q -DskipTests compile

# Ask the assistant through the current chat API
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "list files in src/main/java"}'

# Request a folder summary
curl -X POST http://localhost:8080/api/chat/summarize-folder \
  -H "Content-Type: application/json" \
  -d '{"path": "."}'
```

### Key Documentation

- [Architecture Overview](architecture.md) - Launcher, package, and runtime structure
- [Frontend Guide](frontend.md) - UI architecture and integration
- [API Listing Specification](api-listing-specification.md) - Current route and signal conventions
- [Tool Provider Notes](tool-provider-notes.md) - Linux CLI equivalents
- [Known Issues](known-issues.md) - Current limitations
- [Recommendations](recommendations.md) - Implementation checklist

## Technology Stack

### Backend

- **Quarkus** - Java application framework
- **Jakarta EE** - Dependency injection and CDI
- **LangChain4j** - LLM integration and tool calling
- **Ollama** - Local LLM runtime
- **JLine** - CLI terminal interaction

### Frontend

- **React 18** - UI framework
- **TypeScript** - Type safety
- **Vite 5** - Build tooling
- **react-markdown** - Markdown rendering

### Data Storage

- **Filesystem** - Local folders and project contents being inspected.
- **Panache/JPA** - Persistence for known projects, settings, knowledge artifacts, and telemetry.
- **Database-backed knowledge rows** - Stored markdown summaries and next-step reports keyed by project.
- **Embedding chunks** - Indexed semantic and graph-derived retrieval material.

## Future Directions

1. **Completion Assessment**: Add a typed completion-assessment model with evidence-backed maturity bands and confidence.
2. **Deeper Inspection Planning**: Expand project inspection beyond the root and first folder layer.
3. **Known Project Profiles**: Persist structured project purpose, scope, risks, and maturity alongside markdown summaries.
4. **Cross-Project Comparison**: Compare known projects by purpose, stack, inspection freshness, and maturity.
5. **Historical Re-Inspection**: Track how project understanding and completion estimates change over time.

## Contact and Support

For questions or issues, refer to the project documentation in the [docs](docs) directory or consult the [known-issues](known-issues.md) document for common problems and solutions.
