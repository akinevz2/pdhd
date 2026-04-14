# Frontend Feature Verification

Date: 2026-04-10

## Scope

This report verifies the current end-to-end status of these frontend features:

- Chat
- Opening a GitHub repository link
- Previewing a file
- Summarising a file
- Summarising a folder
- Summarising a project

The verification is code-contract based (frontend expected routes/protocols vs backend implemented entrypoints).

## Verification Method

- Frontend expectations were verified from `App.tsx`, `websocket.ts`, and `utils.ts`.
- Backend availability was verified from Java REST route declarations and websocket endpoint searches under `src/main/java`.
- A full backend route scan was run with ripgrep for `@Path(...)`, websocket markers, and relevant route/protocol strings.

## Feature Status Matrix

| Feature               | Frontend contract                                                                                                 | Backend counterpart                                                                         | Status            |
| --------------------- | ----------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ----------------- |
| Chat                  | REST streaming `POST /api/chat/stream` with JSON `{ message }`, consumed token-by-token in `sendChatMessage(...)` | REST endpoint exists at `/api/chat/stream` and returns streaming text (`text/event-stream`) | Working           |
| Open GitHub repo link | Client-side `openExternalUrl(repoUrl)` after `isBrowsableRepoUrl(...)` check                                      | No backend required                                                                         | Working           |
| Preview file          | `SIGNALS.PROJECT_FILE` -> `GET /api/project/{uuid}/file?entryUuid=...`                                            | No matching backend `@Path` for `/api/project/{uuid}/file` found                            | Broken end-to-end |
| Summarise file        | Uses file-open flow to fetch file content from `PROJECT_FILE`; summary display relies on the same content path    | No matching backend project-file endpoint found                                             | Broken end-to-end |
| Summarise folder      | WebSocket message `{ type: "summarize-folder", projectUuid, uuid }`                                               | No websocket endpoint found to consume typed websocket messages                             | Broken end-to-end |
| Summarise project     | Frontend protocol includes `{ type: "project-next-steps", projectUuid, uuid }`                                    | No websocket endpoint found to consume this message type                                    | Broken end-to-end |

## Detailed Findings

1. Frontend REST contract registration is broader than backend route availability.

`App.tsx` registers signals for:

- `/api/workspace`
- `/api/project`
- `/api/project/{uuid}/browse`
- `/api/project/{uuid}/file?entryUuid=...`
- `/api/chat/reset`

The backend route scan in `src/main/java` found API resources for:

- `/api/chat` (message + stream)
- `/api/menu` (ollama/config/runtime/model endpoints)

No matching backend paths were found for `/api/project/{uuid}/file`, `/api/project/{uuid}/browse`, `/api/workspace`, or `/api/chat/reset` during this verification pass.

2. Chat is now aligned to backend streaming REST endpoints.

- Frontend `sendChatMessage(...)` streams from `POST /api/chat/stream`.
- Backend exposes `/api/chat/stream` in `ChatApiResource` and returns incremental text chunks.
- Chat no longer depends on `/ws/chat` for standard conversational streaming.

Result: core chat streaming is operational with the current backend route contract.

3. Folder/project summarisation protocol is defined in frontend but has no discovered server dispatch.

Frontend websocket message types include:

- `summarize-folder`
- `project-next-steps`

No backend websocket endpoint was found to receive and route these messages.

4. Summarisation persistence pipeline exists but is not exposed by discovered API/websocket entrypoints.

`FileSummarisationPipelineService` exists with:

- `summariseFileAndStore(...)`
- `summariseFolderAndStore(...)`

No production route handler discovered in this pass invokes these methods.

## Conclusion

Current requested feature verification result:

- 2/6 features verified working: chat streaming and opening a GitHub repo link (client-only).
- 4/6 features are currently broken end-to-end due to missing backend entrypoints or websocket protocol gaps for summary flows.

This report reflects current code contracts as of 2026-04-10 and does not include remediation implementation.

## Related Docs

- `docs/frontend.md`
- `docs/chat-service.md`
