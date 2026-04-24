# Frontend Feature Verification

Date: 2026-04-15

## Scope

This report verifies the current end-to-end status of these frontend features:

- Chat
- Opening a GitHub repository link
- Previewing a file
- Summarising a file
- Summarising a folder
- Viewing loaded projects

The verification is code-contract based (frontend expected routes/protocols vs backend implemented entrypoints).

## Verification Method

- Frontend expectations were verified from `App.tsx`, `signalDefinitions.ts`, `PaneWindow.tsx`, and the configuration hooks.
- Backend availability was verified from the resource dispatchers under `src/main/java/ac/uk/sussex/kn253/resources`.
- Verification was based on current signal definitions and the suffix-dispatch resource contract.

## Feature Status Matrix

| Feature                 | Frontend contract                                                                                       | Backend counterpart                                                      | Status  |
| ----------------------- | ------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ | ------- |
| Chat                    | `POST /api/chat/stream` with JSON `{ message }`, consumed incrementally in `sendChatMessage(...)`       | `ChatResource` dispatches `/stream` and returns a streaming text body    | Working |
| Open GitHub repo link   | Client-side `openExternalUrl(repoUrl)` after `isBrowsableRepoUrl(...)` check                            | No backend required                                                      | Working |
| Preview file            | `SIGNALS.PROJECT_FILE` -> `POST /api/project/file`; raw assets use `GET /api/project/{id}/raw?path=...` | `ProjectResource` dispatches `/file` and `/{id}/raw`                     | Working |
| Summarise file/folder   | `SIGNALS.SUMMARY_FILE`, `SIGNALS.SUMMARY_FOLDER`, and `SIGNALS.SUMMARY_STATUS`                          | `SummaryResource` dispatches `/file`, `/folder`, and `/status`           | Working |
| Viewing loaded projects | `SIGNALS.WORKSPACE_LIST` and `SIGNALS.PROJECT_BROWSE`                                                   | `WorkspaceResource` and `ProjectResource` dispatch `/list` and `/browse` | Working |

## Detailed Findings

1. The current frontend contract is signal-based rather than the older `/api/fs`, `/api/projects`, and `/api/cwd` route set.

`signalDefinitions.ts` registers these resource-backed signals:

- `/api/workspace`
- `/api/workspace/list`
- `/api/project/open`
- `/api/project/close`
- `/api/project/remote`
- `/api/project/browse`
- `/api/project/file`
- `/api/summary/folder`
- `/api/summary/file`
- `/api/summary/status`
- `/api/chat/reset`

2. Chat is now aligned to backend streaming REST endpoints.

- Frontend `sendChatMessage(...)` streams from `POST /api/chat/stream`.
- `ChatResource` dispatches `/api/chat/stream` and `/api/chat/reset`.
- The response is consumed as an incremental text stream rather than an SSE-only contract.

3. File preview and markdown asset loading are routed through project signals.

- File content loads via `POST /api/project/file`.
- Raw binary and embedded markdown assets load via `GET /api/project/{id}/raw?path=...`.

4. Folder summarisation is exposed through REST signals.

- The frontend uses `summary:folder`, `summary:file`, and `summary:status`.
- `SummaryResource` invokes `FileSummarisationPipelineService` for folder summaries and persisted file subsummaries.

## Conclusion

Current requested feature verification result:

- The current signal-backed frontend features verified in this pass are wired to matching backend resource dispatchers.
- Legacy websocket helper typings still exist in `websocket.ts`, but they no longer describe the primary summary/chat path used by the UI.

## Related Docs

- `docs/frontend.md`
- `docs/chat-service.md`
