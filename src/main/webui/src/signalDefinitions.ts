import type { ApiSignalDefinition, ApiSignalKey } from "./signals";

export const SIGNALS = {
  TELEMETRY: "telemetry",
  WORKSPACE: "workspace",
  WORKSPACE_LIST: "workspace:list",
  PROJECT_OPEN: "project:open",
  PROJECT_CLOSE: "project:close",
  PROJECT_REMOTE: "project:remote",
  PROJECT_BROWSE: "project:browse",
  PROJECT_FILE: "project:file",
  SUMMARY_FOLDER: "summary:folder",
  SUMMARY_FILE: "summary:file",
  SUMMARY_STATUS: "summary:status",
  CHAT_STREAM: "chat:stream",
  CHAT_RESET: "chat:reset",
} as const satisfies Record<string, ApiSignalKey>;

export function getApiSignalDefinitions(): ReadonlyArray<
  [ApiSignalKey, ApiSignalDefinition<any>]
> {
  return [
    [SIGNALS.TELEMETRY, { method: "GET", endpoint: "/api/telemetry" }],
    [
      SIGNALS.WORKSPACE,
      {
        method: "GET",
        timeoutMs: 5000,
        endpoint: (payload: { path?: string }) => {
          const q = new URLSearchParams();
          if (payload?.path) {
            q.set("path", payload.path);
          }
          const s = q.toString();
          return s ? `/api/workspace?${s}` : "/api/workspace";
        },
      },
    ],
    [SIGNALS.WORKSPACE_LIST, { method: "GET", endpoint: "/api/workspace/list" }],
    [SIGNALS.PROJECT_OPEN, { method: "POST", endpoint: "/api/project/open" }],
    [
      SIGNALS.PROJECT_CLOSE,
      {
        method: "DELETE",
        endpoint: "/api/project/close",
      },
    ],
    [
      SIGNALS.PROJECT_REMOTE,
      {
        method: "POST",
        endpoint: "/api/project/remote",
      },
    ],
    [
      SIGNALS.PROJECT_BROWSE,
      {
        method: "POST",
        endpoint: "/api/project/browse",
      },
    ],
    [
      SIGNALS.PROJECT_FILE,
      {
        method: "POST",
        endpoint: "/api/project/file",
      },
    ],
    [
      SIGNALS.SUMMARY_FOLDER,
      {
        method: "PUT",
        endpoint: "/api/summary/folder",
      },
    ],
    [
      SIGNALS.SUMMARY_FILE,
      {
        method: "PUT",
        endpoint: "/api/summary/file",
      },
    ],
    [
      SIGNALS.SUMMARY_STATUS,
      {
        method: "PUT",
        endpoint: "/api/summary/status",
      },
    ],
    [SIGNALS.CHAT_STREAM, { method: "POST", endpoint: "/api/chat" }],
    [SIGNALS.CHAT_RESET, { method: "DELETE", endpoint: "/api/chat" }],
  ];
}
