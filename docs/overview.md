# PDHD Project Overview

## Project Purpose

**PDHD** (Project Definition Hierarchy Discovery) is an AI-powered development assistant system that provides intelligent filesystem navigation, project understanding, and tool-based interaction capabilities. The system enables developers to interact with their codebase through natural language while leveraging structured tool calls for filesystem operations, project knowledge retrieval, and semantic analysis.

## Core Capabilities

- **Filesystem Exploration**: Navigate and analyze project directories with context-aware search
- **Project Knowledge**: Maintain and query cached project understanding (file structure, patterns, summaries)
- **Tool-Based Interaction**: Execute structured operations via a modular tool system
- **AI-Powered Analysis**: Leverage LLMs for code understanding, summarization, and recommendations
- **Web UI**: Browser-based interface for interactive exploration and chat

## Architecture Overview

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend Layer                       │
│  (React + TypeScript + Vite)                                │
│  - Chat interface                                           │
│  - File browser                                             │
│  - Explorer canvas (floating project windows)               │
└─────────────────────────────────────────────────────────────┘
                              ↓ HTTP
┌─────────────────────────────────────────────────────────────┐
│                     API Layer                               │
│  - ProjectApiResource (filesystem, knowledge)               │
│  - MenuApiResource (settings, configuration)                │
│  - AssistantResource (chat, tool execution)                 │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Service Layer                           │
│  - ToolService (tool dispatch and execution)                │
│  - OllamaChatService (LLM interaction)                      │
│  - WorkingDirectoryService (CWD management)                 │
│  - ProjectKnowledgeRagService (knowledge retrieval)         │
│  - ToolTelemetryService (execution metrics)                 │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Tool Layer                              │
│  - ExploreToolset (filesystem navigation)                   │
│  - ReadToolset (file reading and caching)                   │
│  - WriteToolset (file writing and persistence)              │
│  - IntrospectToolset (project introspection)                │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Data Layer                              │
│  - Filesystem (project directories)                         │
│  - Knowledge cache (sqlite database)                        │
│  - LLM models (Ollama)                                      │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Patterns

1. **Tool Macro Pattern**: Each tool operation is a self-contained `ToolMacro` class with single responsibility
2. **Module-Based Dispatch**: Tools are organized into `ToolModule` implementations with explicit precedence
3. **Alias Resolution**: Tools support canonical names and legacy keyphrases for backward compatibility
4. **Transactional Execution**: Tool execution is wrapped in transactional boundaries with error handling
5. **Caching Strategy**: Read operations cache results with TTL-based invalidation on writes

## Tool Calling System

### Tool Lifecycle

1. **Definition**: Tools are defined as `ToolMacro` subclasses with explicit contracts
2. **Registration**: Tools are grouped into `ToolMacroToolset` implementations
3. **Discovery**: Tools are discovered automatically via CDI at application startup
4. **Invocation**: Tools are invoked via `ToolService.execute()` with typed arguments

### Tool Modules

| Module            | Purpose                             | Key Tools                                                              |
| ----------------- | ----------------------------------- | ---------------------------------------------------------------------- |
| ExploreToolset    | Filesystem discovery and navigation | `list_files`, `search_paths`, `change_cwd`, `get_path_info`            |
| ReadToolset       | File reading and caching            | `read_file`, `read_folder_manifest`, `read_project_knowledge`          |
| WriteToolset      | Output generation and persistence   | `write_file`, `append_project_todo`, `create_plan`                     |
| IntrospectToolset | Session/project introspection       | `read_project_manifest`, `get_session_context`, `read_folder_manifest` |

### Execution Flow

```
LLM Request
    ↓
ToolService.execute(request, memoryId)
    ↓
Module selection (iterative canHandle check)
    ↓
ToolMacroRegistry.resolve(name, args)
    ↓
ToolMacro.execute(args, memoryId)
    ↓
Result returned to LLM
```

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

- File tree loaded from `/api/projects/{id}/tree`
- Text content from `/api/projects/{id}/file?path=...`
- Images from `/api/projects/{id}/file/raw?path=...`
- Chat responses rendered with markdown support
- Project knowledge displayed in explorer canvas

## Project Knowledge System

### Knowledge Structure

Project knowledge is cached in a `.pdhd` directory within each project:

```
.pdhd/
├── knowledge.json          # Main knowledge document
├── cache/
│   ├── file:*             # File content cache
│   ├── path:*             # Path analysis cache
│   └── folder:*           # Folder manifest cache
└── embeddings/            # Semantic embeddings (optional)
```

### Knowledge Document Format

```json
{
  "tag": "project-root",
  "projectDirectory": "/path/to/project",
  "entries": [
    {
      "path": "src/main/java",
      "type": "directory",
      "summary": "Main Java source directory"
    }
  ],
  "timestamp": "2026-04-01T12:00:00Z",
  "source": "folder_manifest",
  "query": "root directory",
  "note": "Initial project scan"
}
```

### Cache Invalidation

- **Write operations**: Invalidate all read caches for the project
- **TTL-based expiration**: Configurable time-to-live for cached entries
- **Explicit refresh**: Tools can force cache regeneration

## Known Issues

### 1. Folder Summary Evidence Leakage

**Issue**: Folder summaries display internal tool evidence markers (e.g., "=== sampled file contents (evidence only) ===") in the final response.

**Impact**: User experience degraded by exposing implementation details.

**Recommendation**: Strip evidence scaffolding before sending to LLM or post-process LLM response.

### 2. Frontend Navigation Gap

**Issue**: Parent directory navigation is a separate button rather than an ".." entry in the folder list.

**Impact**: UI inconsistency with standard file explorer patterns.

**Recommendation**: Integrate ".." entry naturally into folder listing.

### 3. Explore Button Missing

**Issue**: No visible "Explore" button for quick access to opening folders/files in the explorer canvas.

**Impact**: Users must manually arrange windows or use canvas auto-open feature.

**Recommendation**: Add secondary action buttons to browser entries.

### 4. Tool Error Propagation

**Issue**: Errors are silently swallowed in production use, hiding failures from developers.

**Recommendation**: Improve error logging and user visibility.

## Implementation Recommendations

### Priority 1: Critical Fixes

1. **Evidence Leakage**: Implement response filtering for folder summaries
2. **Error Visibility**: Enhance error logging and user notifications
3. **Navigation UX**: Integrate ".." entry into folder listing

### Priority 2: UX Improvements

1. **Explore Buttons**: Add secondary action buttons to browser entries
2. **Cache Status**: Display cache freshness indicators in UI
3. **Tool Telemetry**: Add user-visible tool execution metrics

### Priority 3: Enhancements

1. **Typed Contracts**: Extend API responses with versioned schemas
2. **Performance Monitoring**: Add latency tracking and optimization
3. **Advanced Search**: Enhance path search with semantic understanding

## Development Workflow

### Running the Application

```bash
# From repository root
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
# Run via CLI
./mvnw -q -DskipTests compile

# Execute tool via API
curl -X POST http://localhost:8080/api/assistant/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "list files in src/main/java"}'
```

### Key Documentation

- [Frontend Guide](frontend.md) - UI architecture and integration
- [Tool Calling Architecture](tool-calling-architecture.md) - Runtime control flow
- [Tool Calling Conventions](tool-calling-conventions.md) - Tool definition patterns
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

- **Filesystem** - Project directories and knowledge cache
- **JSON** - Knowledge document format
- **Panache/JPA** - Entity persistence (optional)

## Future Directions

1. **Multi-Project Support**: Enable concurrent project exploration
2. **Collaborative Features**: Add shared knowledge and annotations
3. **Advanced Analytics**: Project health metrics and code quality insights
4. **Plugin System**: Extensible tool ecosystem
5. **Mobile Support**: Responsive UI for on-the-go development

## Contact and Support

For questions or issues, refer to the project documentation in the [docs](docs) directory or consult the [known-issues](known-issues.md) document for common problems and solutions.
