# Frontend Guide

This document explains the PDHD web UI architecture, local workflow, and key integration points with the Quarkus backend.

## Location

Frontend source is under:

- `src/main/webui`

Main files:

- `src/main/webui/src/main.tsx` - React entry point
- `src/main/webui/src/App.tsx` - main application shell and orchestration
- `src/main/webui/src/components` - visual components
- `src/main/webui/src/hooks` - stateful UI hooks
- `src/main/webui/src/api.ts` - typed fetch helpers
- `src/main/webui/src/types.ts` - shared frontend types
- `src/main/webui/src/theme.css` - application styling

## Tech Stack

- React 18 + TypeScript
- Vite 5 build/dev tooling
- `react-markdown` + `remark-gfm` for markdown rendering
- Three.js dependency available in the workspace (used by UI features where applicable)

## Run and Build

From repository root:

```sh
cd src/main/webui
npm install
npm run dev
```

Build production assets:

```sh
cd src/main/webui
npm run build
```

Preview built assets locally:

```sh
cd src/main/webui
npm run preview
```

## UI Overview

The application layout is controlled in `App.tsx` and `theme.css`.

Primary areas:

- Top menu and modals (Ollama config, system prompts, debug)
- Current working directory (CWD) navigator
- File Browser panel (quick filesystem list)
- Assistant chat panel
- Floating project windows (explorer canvas)

## Explorer Canvas

The explorer canvas is implemented with `ProjectWindow` and `TreeView`.

Behavior summary:

- File tree is loaded from `POST /api/project/browse`
- Text file content is loaded from `POST /api/project/file`
- Images are loaded from `GET /api/project/{id}/raw?path=...`
- Folder nodes can trigger one-shot folder summarization via assistant APIs

Markdown support in canvas:

- Markdown files (`.md`, `.markdown`, `.mdx`) render as formatted markdown
- Folder-summary assistant responses also render as markdown
- Non-markdown text still renders in preformatted text mode
- Image files continue to render in image preview mode

This is controlled via `WindowState.fileContentMarkdown` in `types.ts` and rendered in `components/ProjectWindow.tsx`.

## API Integration

Frontend API helpers are in `src/main/webui/src/api.ts`:

- `api<T>(url)` for GET
- `apiPost<TReq, TRes>(url, body, timeoutMs?)` for POST

Timeout behavior:

- General API timeout: `API_TIMEOUT_MS` (default 10s)
- Long chat timeout: `CHAT_TIMEOUT_MS` (default 120s)

These constants are in `src/main/webui/src/utils.ts`.

## Core Endpoints Used by the UI

Filesystem and project endpoints:

- `GET /api/workspace?path=...`
- `GET /api/workspace/list`
- `POST /api/project/open`
- `DELETE /api/project/close`
- `POST /api/project/remote`
- `POST /api/project/browse`
- `POST /api/project/file`
- `GET /api/project/{id}/raw?path=...`
- `PUT /api/summary/folder`
- `PUT /api/summary/file`
- `PUT /api/summary/status`

Assistant and activity endpoints:

- `POST /api/chat`
- `DELETE /api/chat`
- `GET /api/telemetry`

Configuration endpoints:

- `GET /api/menu/ollama`
- `POST /api/menu/ollama`
- `GET /api/menu/ollama/status`
- `GET /api/menu/ollama/models?baseUrl=...`
- `GET /api/menu/ollama/models/pull/stream?modelName=...&baseUrl=...`
- `POST /api/menu/ollama/models/delete`
- `POST /api/menu/ollama/runtime/provider`
- `POST /api/menu/exit`

## State Model (High-Level)

`App.tsx` owns top-level state:

- Browser state (`browserPath`, `browserEntries`, loading/error)
- CWD state (`cwd`, update state, error)
- Chat state (`chatMessages`, input, loading/error)
- Activity stream (`activityItems`)
- Explorer canvas windows (`WindowState[]`)

Important hooks:

- `useMenuPanels` handles modal and settings form state
- `useHighlightedFiles` maps recent tool activity to highlighted file paths in windows

## Adding Features Safely

Recommended workflow:

1. Add or extend types in `types.ts`
2. Add API calls in `api.ts` or typed wrappers in `App.tsx`
3. Update state transitions in `App.tsx`
4. Render in a focused component under `components/`
5. Add any required CSS in `theme.css`
6. Run frontend build (`npm run build`) to catch TS/Vite regressions

For backend-connected features, verify endpoint contracts and default timeouts before wiring UI behavior.

## Troubleshooting

- If assistant requests time out in UI, verify `CHAT_TIMEOUT_MS` usage for chat paths.
- If canvas content does not update, check `WindowState` transitions in `App.tsx`.
- If markdown does not render, verify `fileContentMarkdown` is set for the relevant path/flow.
- If workspace navigation fails, check `/api/workspace` responses in browser network tools.

## Conventions

- Keep API responses strongly typed in `types.ts`
- Prefer explicit loading/error states over implicit assumptions
- Keep long-running assistant requests on chat timeout, not generic API timeout
- Avoid guessing paths in UI logic; use backend suggestions and explicit user actions

## Known UI Gaps

### File Browser: Parent Directory Navigation

**Current Behavior**: The ".." (parent directory) button is rendered as a visually separate, dedicated button above the folder listing.

**Expected Behavior**: The ".." entry should be integrated as part of the folder list, sorted naturally or placed at the top, consistent with standard file browser UX.

**Location**: `App.tsx` around line 740+, the hardcoded `browser-row` for parent directory navigation.

**Impact**: Minor UX friction and visual inconsistency.

---

### File Browser: Explore Actions

**Current Behavior**: The left-pane browser includes an `Explore Current` button plus inline explore actions for folder rows, so opening the canvas is available without waiting for auto-open behavior.

**Notes**: The remaining browser navigation quirk is the dedicated parent-directory row above the listing. That is still a real UX gap, but it is separate from canvas access.

**Location**: `App.tsx`, browser entry rendering and `openInCanvas()` wiring.

---

### Folder Summaries: Tool Evidence Leakage

**Current Behavior**: When `openFolderSummary()` calls the assistant with `read_folder_manifest` evidence, the LLM may reproduce or reference internal evidence markers (e.g., "=== sampled file contents (evidence only) ===") in its final response, which then displays in the canvas.

**Expected Behavior**: Final folder summaries should contain only user-friendly description and analysis, with no evidence scaffolding or tool artifacts visible.

**Location**: `App.tsx` `openFolderSummary()` function (lines 431-540), and backend `IntrospectToolSupport.readFolderManifest()`.

**Impact**: Poor user experience; internal implementation details leak into rendered UI.

**Upstream Fix Needed**: Post-process LLM response to strip evidence markers, or adjust backend to separate evidence from narrative summary.
