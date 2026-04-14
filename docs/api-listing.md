# API Listing

## Frontend Signals

- telemetry:get: GET /api/tool-telemetry. Loads typed tool telemetry summary/items.
- workspace:get: GET /api/workspace. Initializes/reads workspace state at current working directory and returns path, repoUrl, entries.
- project:list: GET /api/project. Lists known project folders.
- project:open: POST /api/project. Registers/opens a project by directory.
- project:close: DELETE /api/project/{id}. Closes/unloads a project window context.
- project:commit-log: GET /api/project/{id}/commit-log. Returns recent git commits.
- project:remote-url: GET /api/project/{id}/remote-url. Returns repository remote URL (if available).
- project:browse: GET /api/project/{id}/browse. Lists indexed entries under a folder.
- project:file: GET /api/project/{id}/file?entryUuid=... . Returns file content/metadata.
- chat:reset: POST /api/chat/reset. Clears server-side chat session state.

## Current Failure Note

- workspace:get

## Signal Error Behavior

- All signal failures emit ApiSignalError with id, signal, endpoint, message, statusCode/contentType (if known), timestamp.
- Failure id is stable per failed invocation and is used as shared state key across chat and canvas.
- Retry is explicit only: retryFailedSignal(failureId) replays the exact failed signal invocation.
- Dismiss is explicit: dismissSignalFailure(failureId) drops retained retry context.
- Retry and Dismiss both remove the visible chat failure box for that failure id.

## HTML/Iframe Behavior

- text/html signal responses (success or HttpResponseError bodies) are published as ApiSignalHtmlFrame events.
- Error-origin HTML frames carry failureId and are linked to the same chat failure item.
- Dismissing a failure also closes iframe windows linked to that failureId.
- Retrying a failure removes old linked iframe(s); if the retried response is HTML, a new iframe is emitted.
- For the same failureId, newly emitted iframe content replaces any existing linked iframe window (no stacking).
