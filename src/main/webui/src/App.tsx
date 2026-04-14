import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { apiPostTextStream, apiWithTimeout } from "./api";
import { normalizeToolCallMarkup } from "./assistantActions";
import { ChatDock } from "./components/ChatDock";
import { CwdNavigator } from "./components/CwdNavigator";
import { FileWindow } from "./components/FileWindow";
import { HtmlFrameWindow } from "./components/HtmlFrameWindow";
import { PaneWindow } from "./components/PaneWindow";
import { TopMenuAndModals } from "./components/TopMenuAndModals";
import { useMenuPanels } from "./hooks/useMenuPanels";
import {
  ConfigurationMenuButtons,
  ConfigurationModals,
} from "./modules/configuration/ConfigurationMenus";
import { useConfigurationMenus } from "./modules/configuration/useConfigurationMenus";
import type {
  ChatMessage,
  FileWindowState,
  FileContentResponse,
  FsBrowserEntry,
  IframeWindowState,
  WorkspaceResponse,
  BrowseResponse,
  ProjectFolderEntity,
  ProjectSummary as ProjectSummaryType,
  ToolActivityItem,
  ToolTelemetryItem,
  ToolTelemetryResponse,
  OllamaRuntimeStatus,
  ApiFailureState,
  WindowState,
} from "./types";
import {
  isBrowsableRepoUrl,
  CHAT_TIMEOUT_MS,
  isImagePath,
  isPdfPath,
  openExternalUrl,
} from "./utils";
import {
  dismissSignalFailure,
  emitApiSignal,
  getSignalErrors,
  registerApiSignals,
  retryFailedSignal,
  subscribeSignalErrors,
  subscribeSignalHtmlFrames,
  type ApiSignalKey,
} from "./signals";

const MARKDOWN_FILE_PATTERN = /\.(md|markdown|mdx)$/i;
const README_FILE_PATTERN = /^readme(\.(md|markdown|mdx|txt))?$/i;
const ASSISTANT_ACTION_BLOCK_PATTERN = /```assistant-action\s*([\s\S]*?)```/gi;
const REQUEST_SOURCE_CHAT_INPUT = "chat-input";
const REQUEST_SOURCE_ASSISTANT_ACTION_BUTTON = "assistant-action-button";
const CWD_GET_TIMEOUT_MS = 10_000;
const OLLAMA_STARTUP_STATUS_TIMEOUT_MS = 1_500;
const OLLAMA_STATUS_POLL_MS = 10_000;
const MOBILE_LAYOUT_BREAKPOINT_PX = 1120;
const MIN_LEFT_RAIL_WIDTH_PX = 220;
const MAX_LEFT_RAIL_WIDTH_PX = 520;
const MIN_RIGHT_RAIL_WIDTH_PX = 240;
const MAX_RIGHT_RAIL_WIDTH_PX = 520;
const MIN_CHAT_HEIGHT_PX = 180;
const MAX_CHAT_HEIGHT_RATIO = 0.55;

type ShellResizer = "left" | "right" | "bottom";

type ResizeDragState = {
  type: ShellResizer;
  startX: number;
  startY: number;
  startLeftWidth: number;
  startRightWidth: number;
  startChatHeight: number;
};

function normalizeApiFailureMessage(
  statusCode: number | undefined,
  message: string,
): string {
  if (typeof statusCode === "number") {
    return `${statusCode}: API unavailable`;
  }
  const statusMatch = /([1-5]\d{2}):\s*API unavailable/.exec(message || "");
  if (statusMatch) {
    return `${statusMatch[1]}: API unavailable`;
  }
  return message;
}

function mergeFailuresById(
  existing: ApiFailureState[],
  incoming: ApiFailureState[],
): ApiFailureState[] {
  const merged = new Map(existing.map((item) => [item.id, item]));
  for (const item of incoming) {
    merged.set(item.id, item);
  }
  return Array.from(merged.values()).sort((a, b) =>
    a.timestamp.localeCompare(b.timestamp),
  );
}

function estimateTokenCount(text: string): number {
  const matches = text.match(/\S+/g);
  return matches ? matches.length : 0;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function toUnixPath(path: string): string {
  return path.replace(/\\/g, "/");
}

function isAbsolutePath(path: string): boolean {
  return /^(\/|[A-Za-z]:[\\/])/.test(path);
}

function resolveTargetPath(
  projectDirectory: string,
  entryPath: string,
): string {
  const base = toUnixPath((projectDirectory || "").trim());
  const raw = toUnixPath((entryPath || "").trim());

  if (!raw || raw === "." || raw === "./") {
    return base;
  }
  if (isAbsolutePath(raw)) {
    return raw;
  }
  if (!base) {
    return raw;
  }
  return `${base}/${raw}`;
}

type AssistantActionPayload = {
  label: string;
  prompt: string;
};

const SIGNALS = {
  TOOL_TELEMETRY_GET: "telemetry:get",
  WORKSPACE_GET: "workspace:get",
  PROJECT_LIST: "project:list",
  PROJECT_OPEN: "project:open",
  PROJECT_CLOSE: "project:close",
  PROJECT_REMOTE_URL: "project:remote-url",
  PROJECT_BROWSE: "project:browse",
  PROJECT_FILE: "project:file",
  CHAT_RESET: "chat:reset",
} as const satisfies Record<string, ApiSignalKey>;

export function App() {
  const appShellRef = useRef<HTMLElement | null>(null);
  const [browserPath, setBrowserPath] = useState<string>("");
  const [browserEntries, setBrowserEntries] = useState<FsBrowserEntry[]>([]);
  const [browserLoading, setBrowserLoading] = useState(false);
  const [browserRepoUrl, setBrowserRepoUrl] = useState<string | null>(null);
  const [browserProjectId, setBrowserProjectId] = useState(0);

  const [activityItems] = useState<ToolActivityItem[]>([]);

  const [telemetryOpen, setTelemetryOpen] = useState(false);
  const [telemetryItems, setTelemetryItems] = useState<ToolTelemetryItem[]>([]);
  const [telemetryLoading, setTelemetryLoading] = useState(false);
  const [telemetrySummary, setTelemetrySummary] = useState<string>("");
  const [telemetryGeneratedAt, setTelemetryGeneratedAt] = useState<string>("");

  const [cwd, setCwd] = useState<string>("");
  const [cwdUpdating, setCwdUpdating] = useState(false);

  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState<string>("");
  const [chatLoading, setChatLoading] = useState<boolean>(false);
  const [apiFailures, setApiFailures] = useState<ApiFailureState[]>([]);
  const [ollamaHealthy, setOllamaHealthy] = useState<boolean | null>(null);
  const [ollamaEndpoint, setOllamaEndpoint] = useState<string>("");
  const [streamingActive, setStreamingActive] = useState<boolean>(false);
  const [streamingTokenCount, setStreamingTokenCount] = useState<number>(0);
  const [streamingTokensPerSecond, setStreamingTokensPerSecond] =
    useState<number>(0);
  const [leftRailWidth, setLeftRailWidth] = useState<number>(300);
  const [rightRailWidth, setRightRailWidth] = useState<number>(320);
  const [chatHeight, setChatHeight] = useState<number>(240);
  const [activeResizer, setActiveResizer] = useState<ShellResizer | null>(null);
  const [chatUnavailable, setChatUnavailable] = useState<boolean>(false);
  const chatLogRef = useRef<HTMLDivElement | null>(null);
  const chatInputRef = useRef<HTMLTextAreaElement | null>(null);
  const initialCwdLoadedRef = useRef(false);
  const streamStartedAtRef = useRef<number>(0);
  const streamTextRef = useRef<string>("");
  const [windows, setWindows] = useState<WindowState[]>([]);
  const [fileWindows, setFileWindows] = useState<FileWindowState[]>([]);
  const [iframeWindows, setIframeWindows] = useState<IframeWindowState[]>([]);
  const nextWindowId = useRef(1);
  const nextZ = useRef(10);
  const resizeDragRef = useRef<ResizeDragState | null>(null);

  const menuPanels = useMenuPanels();
  const configurationMenus = useConfigurationMenus();

  const startShellResize = useCallback(
    (type: ShellResizer, event: React.PointerEvent<HTMLDivElement>) => {
      if (window.innerWidth <= MOBILE_LAYOUT_BREAKPOINT_PX) {
        return;
      }
      event.preventDefault();
      resizeDragRef.current = {
        type,
        startX: event.clientX,
        startY: event.clientY,
        startLeftWidth: leftRailWidth,
        startRightWidth: rightRailWidth,
        startChatHeight: chatHeight,
      };
      setActiveResizer(type);
    },
    [chatHeight, leftRailWidth, rightRailWidth],
  );

  const nudgeShellResize = useCallback((type: ShellResizer, direction: -1 | 1) => {
    if (window.innerWidth <= MOBILE_LAYOUT_BREAKPOINT_PX) {
      return;
    }

    if (type === "left") {
      setLeftRailWidth((current) =>
        clamp(current + direction * 24, MIN_LEFT_RAIL_WIDTH_PX, MAX_LEFT_RAIL_WIDTH_PX),
      );
      return;
    }

    if (type === "right") {
      setRightRailWidth((current) =>
        clamp(current - direction * 24, MIN_RIGHT_RAIL_WIDTH_PX, MAX_RIGHT_RAIL_WIDTH_PX),
      );
      return;
    }

    const shellHeight = appShellRef.current?.clientHeight ?? window.innerHeight;
    const maxChatHeight = Math.max(MIN_CHAT_HEIGHT_PX, Math.floor(shellHeight * MAX_CHAT_HEIGHT_RATIO));
    setChatHeight((current) =>
      clamp(current - direction * 24, MIN_CHAT_HEIGHT_PX, maxChatHeight),
    );
  }, []);

  useEffect(() => {
    const handlePointerMove = (event: PointerEvent) => {
      const dragState = resizeDragRef.current;
      if (!dragState || window.innerWidth <= MOBILE_LAYOUT_BREAKPOINT_PX) {
        return;
      }

      if (dragState.type === "left") {
        const nextWidth = clamp(
          dragState.startLeftWidth + (event.clientX - dragState.startX),
          MIN_LEFT_RAIL_WIDTH_PX,
          MAX_LEFT_RAIL_WIDTH_PX,
        );
        setLeftRailWidth(nextWidth);
        return;
      }

      if (dragState.type === "right") {
        const nextWidth = clamp(
          dragState.startRightWidth - (event.clientX - dragState.startX),
          MIN_RIGHT_RAIL_WIDTH_PX,
          MAX_RIGHT_RAIL_WIDTH_PX,
        );
        setRightRailWidth(nextWidth);
        return;
      }

      const shellHeight = appShellRef.current?.clientHeight ?? window.innerHeight;
      const maxChatHeight = Math.max(MIN_CHAT_HEIGHT_PX, Math.floor(shellHeight * MAX_CHAT_HEIGHT_RATIO));
      const nextHeight = clamp(
        dragState.startChatHeight - (event.clientY - dragState.startY),
        MIN_CHAT_HEIGHT_PX,
        maxChatHeight,
      );
      setChatHeight(nextHeight);
    };

    const stopResizing = () => {
      resizeDragRef.current = null;
      setActiveResizer(null);
    };

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", stopResizing);
    window.addEventListener("pointercancel", stopResizing);

    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", stopResizing);
      window.removeEventListener("pointercancel", stopResizing);
    };
  }, []);

  const shellStyle = {
    "--left-rail-width": `${leftRailWidth}px`,
    "--right-rail-width": `${rightRailWidth}px`,
    "--chat-height": `${chatHeight}px`,
  } as CSSProperties;

  const startStreamingStats = useCallback(() => {
    streamStartedAtRef.current = performance.now();
    streamTextRef.current = "";
    setStreamingTokenCount(0);
    setStreamingTokensPerSecond(0);
    setStreamingActive(true);
  }, []);

  const updateStreamingStats = useCallback((chunk: string) => {
    streamTextRef.current += chunk;
    const tokenCount = estimateTokenCount(streamTextRef.current);
    const elapsedSeconds = Math.max(
      (performance.now() - streamStartedAtRef.current) / 1000,
      0.001,
    );

    setStreamingTokenCount(tokenCount);
    setStreamingTokensPerSecond(tokenCount / elapsedSeconds);
  }, []);

  const finishStreamingStats = useCallback(() => {
    setStreamingActive(false);
  }, []);

  const mapProjectEntityToSummary = useCallback(
    async (project: ProjectFolderEntity): Promise<ProjectSummaryType> => {
      const projectId = Number.isFinite(project.id) ? project.id : -1;

      return {
        id: projectId,
        directory: project.directory,
        hasGitRepository: Boolean(
          project.gitRepository || project.githubRepository,
        ),
        hasGithubRepository: Boolean(project.githubRepository),
        loaded: Boolean(project.loaded),
      };
    },
    [],
  );

  const listProjectSummaries = useCallback(async (): Promise<
    ProjectSummaryType[]
  > => {
    const projects = await emitApiSignal<void, ProjectFolderEntity[]>(
      SIGNALS.PROJECT_LIST,
    );
    return Promise.all(projects.map(mapProjectEntityToSummary));
  }, [mapProjectEntityToSummary]);

  const openProjectSummary = useCallback(
    async (directory: string): Promise<ProjectSummaryType> => {
      const project = await emitApiSignal<
        { directory: string },
        ProjectFolderEntity
      >(SIGNALS.PROJECT_OPEN, { directory });
      return mapProjectEntityToSummary(project);
    },
    [mapProjectEntityToSummary],
  );

  useEffect(() => {
    registerApiSignals([
      [
        SIGNALS.TOOL_TELEMETRY_GET,
        { method: "GET", endpoint: "/api/tool-telemetry" },
      ],
      [
        SIGNALS.WORKSPACE_GET,
        {
          method: "GET",
          timeoutMs: CWD_GET_TIMEOUT_MS,
          endpoint: (payload: { path?: string }) => {
            const q = new URLSearchParams();
            if (payload?.path) q.set("path", payload.path);
            const s = q.toString();
            return s ? `/api/workspace?${s}` : "/api/workspace";
          },
        },
      ],
      [SIGNALS.PROJECT_LIST, { method: "GET", endpoint: "/api/project" }],
      [SIGNALS.PROJECT_OPEN, { method: "POST", endpoint: "/api/project" }],
      [
        SIGNALS.PROJECT_CLOSE,
        {
          method: "DELETE",
          endpoint: (payload: { id: number }) => `/api/project/${payload.id}`,
        },
      ],
      [
        SIGNALS.PROJECT_REMOTE_URL,
        {
          method: "GET",
          endpoint: (payload: { id: number }) =>
            `/api/project/${payload.id}/remote-url`,
        },
      ],
      [
        SIGNALS.PROJECT_BROWSE,
        {
          method: "GET",
          endpoint: (payload: { id: number; parentUuid?: string }) => {
            const q = new URLSearchParams();
            if (payload.parentUuid) q.set("parentUuid", payload.parentUuid);
            const s = q.toString();
            return s
              ? `/api/project/${payload.id}/browse?${s}`
              : `/api/project/${payload.id}/browse`;
          },
        },
      ],
      [
        SIGNALS.PROJECT_FILE,
        {
          method: "GET",
          endpoint: (payload: { id: number; entryUuid: string }) =>
            `/api/project/${payload.id}/file?entryUuid=${encodeURIComponent(payload.entryUuid)}`,
        },
      ],
      [SIGNALS.CHAT_RESET, { method: "POST", endpoint: "/api/chat/reset" }],
    ]);
  }, []);

  useEffect(() => {
    const checkOllamaRuntime = async () => {
      try {
        const status = await apiWithTimeout<OllamaRuntimeStatus>(
          "/api/menu/ollama/status",
          OLLAMA_STARTUP_STATUS_TIMEOUT_MS,
        );
        setOllamaHealthy(status?.healthy ?? null);
        setOllamaEndpoint(status?.runtimeEndpoint || "");
      } catch {
        setOllamaHealthy(false);
        // non-fatal: startup status check is best-effort
      }
    };

    const timeoutId = window.setTimeout(() => {
      checkOllamaRuntime().catch(() => {});
    }, 0);
    const intervalId = window.setInterval(() => {
      checkOllamaRuntime().catch(() => {});
    }, OLLAMA_STATUS_POLL_MS);

    return () => {
      window.clearTimeout(timeoutId);
      window.clearInterval(intervalId);
    };
  }, []);

  useEffect(() => {
    const toFailureState = (signalError: {
      id: string;
      signal: string;
      endpoint: string;
      message: string;
      statusCode?: number;
      contentType?: string | null;
      timestamp: string;
    }): ApiFailureState => ({
      id: signalError.id,
      signal: signalError.signal,
      endpoint: signalError.endpoint,
      message: normalizeApiFailureMessage(
        signalError.statusCode,
        signalError.message,
      ),
      statusCode: signalError.statusCode,
      contentType: signalError.contentType,
      timestamp: signalError.timestamp,
      iframeWindowIds: [],
    });

    // Capture failures that may have occurred before this component subscribed.
    setApiFailures((prev) =>
      mergeFailuresById(prev, getSignalErrors().map(toFailureState)),
    );

    const unsubscribe = subscribeSignalErrors((signalError) => {
      setApiFailures((prev) =>
        mergeFailuresById(prev, [toFailureState(signalError)]),
      );
    });

    return unsubscribe;
  }, []);

  useEffect(() => {
    setApiFailures((prev) =>
      prev.map((failure) => {
        const message = normalizeApiFailureMessage(
          failure.statusCode,
          failure.message,
        );
        return message === failure.message ? failure : { ...failure, message };
      }),
    );
  }, []);

  useEffect(() => {
    const unsubscribe = subscribeSignalHtmlFrames((frame) => {
      const nextId = ++nextWindowId.current;
      const title =
        typeof frame.statusCode === "number"
          ? `${frame.signal} (${frame.statusCode})`
          : frame.signal;

      setIframeWindows((prev) => [
        ...(frame.failureId
          ? prev.filter(
              (windowState) => windowState.failureId !== frame.failureId,
            )
          : prev),
        {
          id: nextId,
          failureId: frame.failureId,
          title,
          html: frame.html,
          signal: frame.signal,
          endpoint: frame.endpoint,
          statusCode: frame.statusCode,
          z: ++nextZ.current,
        },
      ]);

      if (frame.failureId) {
        setApiFailures((prev) =>
          prev.map((failure) =>
            failure.id === frame.failureId &&
            !failure.iframeWindowIds.includes(nextId)
              ? {
                  ...failure,
                  iframeWindowIds: [...failure.iframeWindowIds, nextId],
                }
              : failure,
          ),
        );
      }
    });

    return unsubscribe;
  }, []);

  const loadTelemetry = useCallback(async () => {
    setTelemetryLoading(true);
    try {
      const data = await emitApiSignal<void, ToolTelemetryResponse>(
        SIGNALS.TOOL_TELEMETRY_GET,
      );
      setTelemetryItems(data.items || []);
      setTelemetrySummary(data.summary || "");
      setTelemetryGeneratedAt(data.generatedAt || "");
    } catch {
      // errors are surfaced by signal failure state
    } finally {
      setTelemetryLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTelemetry().catch(() => {
      // errors are surfaced by signal failure state
    });

    const intervalId = window.setInterval(() => {
      loadTelemetry().catch(() => {
        // errors are surfaced by signal failure state
      });
    }, 15_000);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [loadTelemetry]);

  const loadCwd = useCallback(
    async (path?: string) => {
      setBrowserLoading(true);
      try {
        const data = await emitApiSignal<{ path?: string }, WorkspaceResponse>(
          SIGNALS.WORKSPACE_GET,
          path ? { path } : {},
        );
        const targetPath = data.path || "";
        setCwd(data.path || "");
        setBrowserPath(data.path || "");
        setBrowserEntries(data.entries || []);
        setBrowserRepoUrl(data.repoUrl || null);

        try {
          const projects = await listProjectSummaries();
          const projectMatch = projects
            .filter(
              (project) =>
                targetPath === project.directory ||
                targetPath.startsWith(`${project.directory}/`) ||
                targetPath.startsWith(`${project.directory}\\`),
            )
            .sort((a, b) => b.directory.length - a.directory.length)[0];

          setBrowserProjectId(projectMatch?.id || 0);
        } catch {
          setBrowserProjectId(0);
        }
      } catch {
        setBrowserRepoUrl(null);
        setBrowserProjectId(0);
      } finally {
        setBrowserLoading(false);
      }
    },
    [listProjectSummaries],
  );

  const browserInRegisteredProject = browserProjectId > 0;

  const setWorkingFolder = useCallback(
    async (path: string) => {
      setCwdUpdating(true);
      try {
        await loadCwd(path);
      } catch {
        // errors are surfaced by signal failure state
      } finally {
        setCwdUpdating(false);
      }
    },
    [loadCwd],
  );

  useEffect(() => {
    if (initialCwdLoadedRef.current) {
      return;
    }
    initialCwdLoadedRef.current = true;
    loadCwd().catch(() => {
      // errors are surfaced by signal failure state
    });
  }, [loadCwd]);

  const focusWindow = useCallback((id: number) => {
    setWindows((prev) =>
      prev.map((w) =>
        w.id === id
          ? {
              ...w,
              z: ++nextZ.current,
            }
          : w,
      ),
    );
  }, []);

  const closeWindow = useCallback(async (id: number, projectId: number) => {
    setWindows((prev) => prev.filter((w) => w.id !== id));
    if (!projectId) {
      return;
    }
    try {
      await emitApiSignal<{ id: number }, void>(SIGNALS.PROJECT_CLOSE, {
        id: projectId,
      });
    } catch {
      // non-fatal: local close should still succeed
    }
  }, []);

  const closeFileWindow = useCallback((id: number) => {
    setFileWindows((prev) => prev.filter((w) => w.id !== id));
  }, []);

  const closeIframeWindow = useCallback(
    (id: number) => {
      const target = iframeWindows.find((windowState) => windowState.id === id);
      if (target?.failureId) {
        setApiFailures((prev) =>
          prev.map((failure) =>
            failure.id === target.failureId
              ? {
                  ...failure,
                  iframeWindowIds: failure.iframeWindowIds.filter(
                    (windowId) => windowId !== id,
                  ),
                }
              : failure,
          ),
        );
      }

      setIframeWindows((prev) =>
        prev.filter((windowState) => windowState.id !== id),
      );
    },
    [iframeWindows],
  );

  const updateWindow = useCallback(
    (id: number, patch: Partial<WindowState>) => {
      setWindows((prev) =>
        prev.map((w) => (w.id === id ? { ...w, ...patch } : w)),
      );
    },
    [],
  );

  const loadEntriesForWindow = useCallback(
    async (windowId: number, projectId: number) => {
      if (!projectId) {
        updateWindow(windowId, { entriesLoading: false });
        return;
      }
      try {
        const response = await emitApiSignal<
          { id: number; parentUuid?: string },
          BrowseResponse
        >(SIGNALS.PROJECT_BROWSE, { id: projectId });
        updateWindow(windowId, {
          entries: response.entries || [],
          entriesLoading: false,
          entriesError: undefined,
        });
      } catch {
        updateWindow(windowId, {
          entriesLoading: false,
          entriesError: undefined,
        });
      }
    },
    [updateWindow],
  );

  const openInCanvas = useCallback(
    async (
      path: string,
    ): Promise<{ windowId: number; projectId: number } | null> => {
      const target = path || cwd;
      if (!target) return null;
      const projects = await listProjectSummaries();
      const match = projects.find((p) => p.directory === target);

      let project: ProjectSummaryType;
      if (match) {
        project = match;
        if (!project.loaded) {
          try {
            project = await openProjectSummary(project.directory);
          } catch {
            project = { ...project, loaded: true };
          }
        }
      } else {
        try {
          project = await openProjectSummary(target);
          if (project.warning) {
            setChatMessages((prev) => [
              ...prev,
              { role: "system", content: `⚠️ ${project.warning}` },
            ]);
          }
        } catch {
          // fallback to transient project if registration fails
          project = {
            id: -(
              Math.abs(
                target.split("").reduce((acc, c) => acc + c.charCodeAt(0), 0),
              ) + 1
            ),
            directory: target,
            hasGitRepository: false,
            hasGithubRepository: false,
            loaded: false,
          };
        }
      }

      const existing = windows.find(
        (w) => w.project.directory === project.directory,
      );
      if (existing) {
        focusWindow(existing.id);
        setWorkingFolder(project.directory).catch(() => {});
        return { windowId: existing.id, projectId: project.id };
      }
      const winId = ++nextWindowId.current;
      setWindows((prev) => [
        ...prev,
        {
          id: winId,
          project,
          entriesLoading: true,
          x: 60 + (winId % 6) * 30,
          y: 40 + (winId % 6) * 30,
          z: ++nextZ.current,
        },
      ]);
      await Promise.allSettled([loadEntriesForWindow(winId, project.id)]);
      return { windowId: winId, projectId: project.id };
    },
    [
      cwd,
      windows,
      focusWindow,
      listProjectSummaries,
      loadEntriesForWindow,
      openProjectSummary,
    ],
  );

  const restoredOpenProjectsRef = useRef(false);

  useEffect(() => {
    if (restoredOpenProjectsRef.current) {
      return;
    }
    restoredOpenProjectsRef.current = true;

    const restoreOpenProjects = async () => {
      try {
        const projects = await listProjectSummaries();
        const openProjects = projects.filter((p) => p.loaded);
        const restoredWindows = openProjects.map((project) => {
          const winId = ++nextWindowId.current;
          return {
            id: winId,
            project,
            entriesLoading: true,
            x: 60 + (winId % 6) * 30,
            y: 40 + (winId % 6) * 30,
            z: ++nextZ.current,
          };
        });

        if (restoredWindows.length === 0) {
          return;
        }

        setWindows((prev) => {
          const existingProjectIds = new Set(prev.map((w) => w.project.id));
          return [
            ...prev,
            ...restoredWindows.filter(
              (windowState) => !existingProjectIds.has(windowState.project.id),
            ),
          ];
        });

        await Promise.allSettled(
          restoredWindows.map((windowState) =>
            Promise.allSettled([
              loadEntriesForWindow(windowState.id, windowState.project.id),
            ]),
          ),
        );
      } catch {
        // non-fatal during startup restoration
      }
    };

    restoreOpenProjects().catch(() => {
      // surfaced as signal errors if needed
    });
  }, [listProjectSummaries, loadEntriesForWindow]);

  const openFileWindow = useCallback(
    async (filePath: string, projectId: number, entryUuid: string) => {
      if (!projectId || !entryUuid) {
        return;
      }
      const id = ++nextWindowId.current;
      const title = filePath.split(/[\\/]/).pop() || filePath;
      setFileWindows((prev) => [
        ...prev,
        {
          id,
          filePath,
          title,
          loading: true,
          x: 80 + (id % 6) * 24,
          y: 70 + (id % 6) * 24,
          z: ++nextZ.current,
        },
      ]);

      try {
        const file = await emitApiSignal<
          { id: number; entryUuid: string },
          FileContentResponse
        >(SIGNALS.PROJECT_FILE, { id: projectId, entryUuid });
        setFileWindows((prev) =>
          prev.map((w) =>
            w.id === id
              ? {
                  ...w,
                  loading: false,
                  content: file.content,
                  language: file.language,
                  mimeType: file.mimeType,
                  error: undefined,
                }
              : w,
          ),
        );
      } catch {
        setFileWindows((prev) =>
          prev.map((w) =>
            w.id === id
              ? {
                  ...w,
                  loading: false,
                  error: undefined,
                }
              : w,
          ),
        );
      }
    },
    [],
  );

  const focusFileWindow = useCallback((id: number) => {
    setFileWindows((prev) =>
      prev.map((w) =>
        w.id === id
          ? {
              ...w,
              z: ++nextZ.current,
            }
          : w,
      ),
    );
  }, []);

  const moveFileWindow = useCallback((id: number, x: number, y: number) => {
    setFileWindows((prev) =>
      prev.map((w) => (w.id === id ? { ...w, x, y } : w)),
    );
  }, []);

  const focusIframeWindow = useCallback((id: number) => {
    setIframeWindows((prev) =>
      prev.map((w) =>
        w.id === id
          ? {
              ...w,
              z: ++nextZ.current,
            }
          : w,
      ),
    );
  }, []);

  const openGitRepo = useCallback((repoUrl: string | null | undefined) => {
    if (!isBrowsableRepoUrl(repoUrl)) {
      return;
    }
    openExternalUrl(repoUrl!);
  }, []);

  const openFile = useCallback(
    async (
      windowId: number,
      relativePath: string,
      projectId: number,
      entryUuid: string,
    ) => {
      if (isImagePath(relativePath) || isPdfPath(relativePath)) {
        updateWindow(windowId, {
          selectedFilePath: relativePath,
          selectedFileUuid: entryUuid,
          fileLoading: false,
          fileLoadingFolderSummary: false,
          fileContent: undefined,
          fileContentMarkdown: false,
          fileError: undefined,
        });
        return;
      }

      updateWindow(windowId, {
        selectedFilePath: relativePath,
        selectedFileUuid: entryUuid,
        fileLoading: true,
        fileLoadingFolderSummary: false,
        fileContentMarkdown:
          MARKDOWN_FILE_PATTERN.test(relativePath) ||
          README_FILE_PATTERN.test(relativePath),
        fileError: undefined,
      });
      try {
        const file = await emitApiSignal<
          { id: number; entryUuid: string },
          FileContentResponse
        >(SIGNALS.PROJECT_FILE, { id: projectId, entryUuid });
        updateWindow(windowId, {
          fileLoading: false,
          fileLoadingFolderSummary: false,
          fileContent: file.content,
          fileContentMarkdown:
            MARKDOWN_FILE_PATTERN.test(relativePath) ||
            README_FILE_PATTERN.test(relativePath),
          fileError: undefined,
        });
      } catch {
        updateWindow(windowId, {
          fileLoading: false,
          fileLoadingFolderSummary: false,
          fileContentMarkdown: false,
          fileError: undefined,
        });
      }
    },
    [updateWindow],
  );

  const openFolderPreview = useCallback(
    async (
      windowId: number,
      projectDirectory: string,
      relativePath: string,
      entryUuid?: string | null,
      projectId?: number,
    ) => {
      if (!entryUuid || !projectId) {
        return;
      }
      const safeRelativePath = relativePath || "";
      const folderPath = resolveTargetPath(projectDirectory, safeRelativePath);

      updateWindow(windowId, {
        selectedFilePath: safeRelativePath || ".",
        fileLoading: true,
        fileLoadingFolderSummary: true,
        fileContentMarkdown: true,
        fileError: undefined,
      });

      try {
        const queue: Array<{ uuid: string; depth: number }> = [
          { uuid: entryUuid, depth: 0 },
        ];
        const visited = new Set<string>();
        const extensionCounts = new Map<string, number>();
        let fileCount = 0;
        let folderCount = 0;
        let maxDepth = 0;

        while (queue.length > 0) {
          const next = queue.shift();
          if (!next) {
            break;
          }
          if (visited.has(next.uuid)) {
            continue;
          }
          visited.add(next.uuid);
          maxDepth = Math.max(maxDepth, next.depth);

          const response = await emitApiSignal<
            { id: number; parentUuid?: string },
            BrowseResponse
          >(SIGNALS.PROJECT_BROWSE, {
            id: projectId,
            parentUuid: next.uuid,
          });

          for (const entry of response.entries || []) {
            if (entry.directory) {
              folderCount += 1;
              if (entry.uuid && !visited.has(entry.uuid)) {
                queue.push({ uuid: entry.uuid, depth: next.depth + 1 });
              }
              continue;
            }

            fileCount += 1;
            const dot = entry.name.lastIndexOf(".");
            const ext =
              dot > 0 && dot < entry.name.length - 1
                ? entry.name.slice(dot + 1).toLowerCase()
                : "(no ext)";
            extensionCounts.set(ext, (extensionCounts.get(ext) || 0) + 1);
          }
        }

        const topExtensions = Array.from(extensionCounts.entries())
          .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
          .slice(0, 12);

        const extensionTable =
          topExtensions.length === 0
            ? "No files found."
            : [
                "| Extension | Count |",
                "|---|---:|",
                ...topExtensions.map(([ext, count]) => `| ${ext} | ${count} |`),
              ].join("\n");

        const preview = [
          `# Folder Preview`,
          "",
          `Path: ${folderPath}`,
          "",
          `- Files: ${fileCount}`,
          `- Folders: ${folderCount}`,
          `- Total items: ${fileCount + folderCount}`,
          `- Traversed folders: ${visited.size}`,
          `- Max depth: ${maxDepth}`,
          "",
          "## File Types (Top 12)",
          extensionTable,
        ].join("\n");

        updateWindow(windowId, {
          fileLoading: false,
          fileLoadingFolderSummary: false,
          fileError: undefined,
          fileContent: preview,
          fileContentMarkdown: true,
        });
      } catch {
        updateWindow(windowId, {
          fileLoading: false,
          fileLoadingFolderSummary: false,
          fileContentMarkdown: false,
          fileError: undefined,
        });
      }
    },
    [updateWindow],
  );

  const moveWindow = useCallback(
    (id: number, x: number, y: number) => {
      updateWindow(id, { x, y });
    },
    [updateWindow],
  );

  const [retryMessage, setRetryMessage] = useState<string | null>(null);

  const dismissApiError = useCallback((id: string) => {
    dismissSignalFailure(id);
    setApiFailures((prev) => prev.filter((entry) => entry.id !== id));
    setIframeWindows((prev) =>
      prev.filter((windowState) => windowState.failureId !== id),
    );
  }, []);

  const retryApiError = useCallback(async (id: string) => {
    try {
      await retryFailedSignal(id);
    } catch {
      // failed retries are surfaced via signal errors
    } finally {
      dismissSignalFailure(id);
      setApiFailures((prev) => prev.filter((entry) => entry.id !== id));
      setIframeWindows((prev) =>
        prev.filter((windowState) => windowState.failureId !== id),
      );
    }
  }, []);

  const parseAssistantActionPayload = useCallback(
    (rawPayload: string): AssistantActionPayload | null => {
      try {
        const parsed = JSON.parse(rawPayload) as {
          label?: unknown;
          prompt?: unknown;
        } | null;
        const label =
          typeof parsed?.label === "string" ? parsed.label.trim() : "";
        const prompt =
          typeof parsed?.prompt === "string" ? parsed.prompt.trim() : "";
        if (!label || !prompt) {
          return null;
        }
        return { label, prompt };
      } catch {
        return null;
      }
    },
    [],
  );

  const sendChatMessage = useCallback(
    async (
      overrideMessage?: string,
      _source: string = REQUEST_SOURCE_CHAT_INPUT,
    ) => {
      const message = (overrideMessage ?? chatInput).trim();
      if (!message || chatLoading) {
        return;
      }

      setChatLoading(true);
      if (!overrideMessage) setChatInput("");

      // Add the user turn and a blank assistant placeholder that gets filled token-by-token
      const placeholderId = `streaming-${Date.now()}`;
      setChatMessages((prev) => [
        ...prev,
        { role: "user", content: message },
        { role: "assistant", content: "", id: placeholderId },
      ]);
      setRetryMessage(null);

      setTimeout(() => {
        if (chatInputRef.current) chatInputRef.current.focus();
      }, 0);

      let errorOccurred = false;
      startStreamingStats();
      await apiPostTextStream(
        "/api/chat/stream",
        { message },
        {
          onChunk: (fragment) => {
            updateStreamingStats(fragment);
            setChatMessages((prev) =>
              prev.map((msg) =>
                msg.id === placeholderId
                  ? { ...msg, content: msg.content + fragment }
                  : msg,
              ),
            );
          },
          onError: (detail) => {
            errorOccurred = true;
            setChatUnavailable(true);
            setRetryMessage(message);
            setChatMessages((prev) =>
              prev.map((msg) =>
                msg.id === placeholderId
                  ? { ...msg, content: `_(error: ${detail})_` }
                  : msg,
              ),
            );
          },
          onDone: () => {
            if (!errorOccurred) {
              setChatUnavailable(false);
              setRetryMessage(null);
            }
            finishStreamingStats();
            setChatLoading(false);
          },
        },
        CHAT_TIMEOUT_MS,
      );
    },
    [
      chatInput,
      chatLoading,
      finishStreamingStats,
      startStreamingStats,
      updateStreamingStats,
    ],
  );

  const renderAssistantMarkdown = useCallback(
    (content: string) => {
      const normalizedContent = normalizeToolCallMarkup(content);
      const parts: React.ReactNode[] = [];
      const regex = new RegExp(ASSISTANT_ACTION_BLOCK_PATTERN);
      let start = 0;
      let index = 0;
      let match: RegExpExecArray | null;

      while ((match = regex.exec(normalizedContent)) !== null) {
        const before = normalizedContent.slice(start, match.index);
        if (before.trim()) {
          parts.push(
            <ReactMarkdown
              key={`assistant-md-${index++}`}
              remarkPlugins={[remarkGfm]}
            >
              {before}
            </ReactMarkdown>,
          );
        }

        const payload = parseAssistantActionPayload(match[1] || "");
        if (payload) {
          parts.push(
            <button
              key={`assistant-action-${index++}`}
              className="assistant-action-button"
              disabled={chatLoading}
              onClick={() => {
                sendChatMessage(
                  payload.prompt,
                  REQUEST_SOURCE_ASSISTANT_ACTION_BUTTON,
                ).catch(() => {
                  // handled in callback
                });
              }}
              title={payload.prompt}
            >
              {payload.label}
            </button>,
          );
        } else {
          parts.push(
            <ReactMarkdown
              key={`assistant-md-fallback-${index++}`}
              remarkPlugins={[remarkGfm]}
            >
              {match[0]}
            </ReactMarkdown>,
          );
        }

        start = regex.lastIndex;
      }

      const tail = normalizedContent.slice(start);
      if (tail.trim()) {
        parts.push(
          <ReactMarkdown key={`assistant-md-tail`} remarkPlugins={[remarkGfm]}>
            {tail}
          </ReactMarkdown>,
        );
      }

      if (parts.length === 0) {
        return (
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {normalizedContent}
          </ReactMarkdown>
        );
      }
      return parts;
    },
    [chatLoading, parseAssistantActionPayload, sendChatMessage],
  );

  const resetChat = useCallback(async () => {
    try {
      await emitApiSignal<Record<string, never>, unknown>(
        SIGNALS.CHAT_RESET,
        {},
      );
      setChatMessages([]);
    } catch {
      // errors are surfaced by signal failure state
    }
  }, []);

  useEffect(() => {
    if (chatLogRef.current) {
      chatLogRef.current.scrollTop = chatLogRef.current.scrollHeight;
    }
  }, [chatMessages, chatLoading]);

  return (
    <>
      <main id="app-shell" ref={appShellRef} style={shellStyle}>
        <TopMenuAndModals
          menuButtons={
            <ConfigurationMenuButtons
              configLoading={configurationMenus.configLoading}
              onOpenConfiguration={configurationMenus.openConfiguration}
            />
          }
          modalContent={
            <ConfigurationModals
              configOpen={configurationMenus.configOpen}
              configForm={configurationMenus.configForm}
              configFields={configurationMenus.configFields}
              configError={configurationMenus.configError}
              configSaving={configurationMenus.configSaving}
              currentProvider={configurationMenus.currentProvider}
              supportsManagedModels={configurationMenus.supportsManagedModels}
              availableModels={configurationMenus.availableModels}
              modelsLoading={configurationMenus.modelsLoading}
              pullProgress={configurationMenus.pullProgress}
              runtimeStatus={configurationMenus.runtimeStatus}
              setConfigOpen={configurationMenus.setConfigOpen}
              setConfigForm={configurationMenus.setConfigForm}
              refreshModels={configurationMenus.refreshModels}
              pullModel={configurationMenus.pullModel}
              deleteModel={configurationMenus.deleteModel}
              saveConfiguration={configurationMenus.saveConfiguration}
            />
          }
          onOpenDebug={() => menuPanels.setDebugOpen(true)}
          onOpenTelemetry={() => {
            setTelemetryOpen(true);
            loadTelemetry().catch(() => {
              // surfaced by telemetry state
            });
          }}
          onExit={menuPanels.handleExit}
          debugOpen={menuPanels.debugOpen}
          setDebugOpen={menuPanels.setDebugOpen}
          cwd={cwd}
          activityItems={activityItems}
          telemetryOpen={telemetryOpen}
          setTelemetryOpen={setTelemetryOpen}
          telemetryItems={telemetryItems}
          telemetryLoading={telemetryLoading}
          telemetryError={null}
          telemetrySummary={telemetrySummary}
          telemetryGeneratedAt={telemetryGeneratedAt}
          onRefreshTelemetry={loadTelemetry}
        />
        <header className="cwd-bar panel">
          <strong>Working Folder</strong>
          <CwdNavigator cwd={cwd} onNavigate={setWorkingFolder} />
        </header>

        <section className="left-rail panel">
          <div className="toolbar">
            <strong>File Browser</strong>
          </div>

          <div className="left-rail-scroll">
            <div id="projectList">
              <div className="browser-location-row">
                <div className="browser-location-copy">
                  <span className="browser-location-label">Current folder</span>
                  <p
                    className="browser-location-path"
                    title={browserPath || cwd}
                  >
                    {browserPath || cwd}
                  </p>
                </div>
                {isBrowsableRepoUrl(browserRepoUrl) && (
                  <button
                    className="repo-popout-button"
                    onClick={() => openExternalUrl(browserRepoUrl!)}
                    title="Open repository in browser"
                    aria-label="Open repository in browser"
                  >
                    ↗
                  </button>
                )}
              </div>
              {browserLoading && <p>Loading files...</p>}
              {!browserLoading && browserEntries.length === 0 && (
                <p>Folder is empty.</p>
              )}
              {!browserLoading && (
                <>
                  {/* Parent directory entry */}
                  <div key=".." className="browser-row">
                    <button
                      className="browser-row-main"
                      onClick={() => {
                        const active = browserPath || cwd;
                        const normalized = active.replace(/\\/g, "/");
                        const hasDrive = /^[A-Za-z]:\//.test(normalized);
                        const isAbsolute =
                          normalized.startsWith("/") || hasDrive;
                        if (!isAbsolute) {
                          return;
                        }
                        const parts = normalized.split("/").filter(Boolean);
                        const parentParts = parts.slice(0, -1);
                        const parent = hasDrive
                          ? `${normalized.slice(0, 2)}/${parentParts.join("/")}`
                          : `/${parentParts.join("/")}`;
                        setWorkingFolder(parent || active).catch(() => {});
                      }}
                      disabled={cwdUpdating}
                      title="Parent directory"
                    >
                      <span className="browser-row-icon" aria-hidden="true">
                        ▸
                      </span>
                      <span className="browser-row-name">..</span>
                    </button>
                  </div>

                  {/* Filing system entries */}
                  {browserEntries.map((entry) => (
                    <div key={entry.uuid} className="browser-row">
                      {entry.directory ? (
                        <>
                          <button
                            className="browser-row-main"
                            onClick={() => {
                              setWorkingFolder(entry.path).catch(() => {
                                // handled in setWorkingFolder
                              });
                            }}
                            title={entry.path}
                          >
                            <span
                              className="browser-row-icon"
                              aria-hidden="true"
                            >
                              ▸
                            </span>
                            <span className="browser-row-name">
                              {entry.name}
                            </span>
                          </button>
                          <button
                            className="browser-row-action explore-button"
                            onClick={() => {
                              setWorkingFolder(entry.path).catch(() => {
                                // handled in setWorkingFolder
                              });
                            }}
                            title={`Open ${entry.name}`}
                            aria-label={`Open ${entry.name}`}
                          >
                            →
                          </button>
                        </>
                      ) : (
                        <>
                          <button
                            className="browser-row-main browser-row-main-static"
                            title={entry.path}
                            onClick={() => {
                              if (!browserInRegisteredProject) {
                                openInCanvas(browserPath || cwd).catch(
                                  () => {},
                                );
                                return;
                              }
                              openFileWindow(
                                entry.path,
                                browserProjectId,
                                entry.uuid,
                              ).catch(() => {});
                            }}
                          >
                            <span
                              className="browser-row-icon browser-row-icon-file"
                              aria-hidden="true"
                            >
                              •
                            </span>
                            <span className="browser-row-name">
                              {entry.name}
                            </span>
                          </button>
                        </>
                      )}
                      {entry.directory && isBrowsableRepoUrl(entry.repoUrl) && (
                        <button
                          className="repo-popout-button browser-row-action"
                          onClick={() => openExternalUrl(entry.repoUrl!)}
                          title="Open repository in browser"
                          aria-label={`Open repository for ${entry.name}`}
                        >
                          ↗
                        </button>
                      )}
                    </div>
                  ))}
                </>
              )}
            </div>
          </div>

          <button
            className="left-rail-explore-btn"
            onClick={() => {
              openInCanvas(browserPath || cwd).catch(() => {});
            }}
            disabled={!browserPath && !cwd}
            title="Open folder in canvas"
          >
            Explore Current
          </button>
        </section>

        <div
          className={`shell-resizer shell-resizer-vertical ${activeResizer === "left" ? "is-active" : ""}`}
          role="separator"
          aria-label="Resize file browser"
          aria-orientation="vertical"
          tabIndex={0}
          onPointerDown={(event) => startShellResize("left", event)}
          onKeyDown={(event) => {
            if (event.key === "ArrowLeft") {
              event.preventDefault();
              nudgeShellResize("left", -1);
            }
            if (event.key === "ArrowRight") {
              event.preventDefault();
              nudgeShellResize("left", 1);
            }
          }}
        />

        <section className="window-host panel">
          {windows.map((win) => (
            <PaneWindow
              key={win.id}
              windowState={win}
              onClose={() => closeWindow(win.id, win.project.id)}
              onFocus={() => focusWindow(win.id)}
              onMove={(x, y) => moveWindow(win.id, x, y)}
              onOpenFile={(path, uuid) =>
                openFile(win.id, path, win.project.id, uuid)
              }
              onOpenFolderPreview={(path, uuid) =>
                openFolderPreview(
                  win.id,
                  win.project.directory,
                  path,
                  uuid,
                  win.project.id,
                )
              }
              onAssistantAction={(prompt) => {
                sendChatMessage(
                  prompt,
                  REQUEST_SOURCE_ASSISTANT_ACTION_BUTTON,
                ).catch(() => {
                  // handled in callback
                });
              }}
              assistantActionDisabled={chatLoading}
            />
          ))}
          {fileWindows.map((fileWin) => (
            <FileWindow
              key={`file-${fileWin.id}`}
              windowState={fileWin}
              onClose={() => closeFileWindow(fileWin.id)}
              onFocus={() => focusFileWindow(fileWin.id)}
              onMove={(x, y) => moveFileWindow(fileWin.id, x, y)}
            />
          ))}
          {iframeWindows.map((iframeWin) => (
            <HtmlFrameWindow
              key={`iframe-${iframeWin.id}`}
              windowState={iframeWin}
              onClose={() => closeIframeWindow(iframeWin.id)}
              onFocus={() => focusIframeWindow(iframeWin.id)}
            />
          ))}
        </section>

        <div
          className={`shell-resizer shell-resizer-vertical ${activeResizer === "right" ? "is-active" : ""}`}
          role="separator"
          aria-label="Resize assistant activity"
          aria-orientation="vertical"
          tabIndex={0}
          onPointerDown={(event) => startShellResize("right", event)}
          onKeyDown={(event) => {
            if (event.key === "ArrowLeft") {
              event.preventDefault();
              nudgeShellResize("right", -1);
            }
            if (event.key === "ArrowRight") {
              event.preventDefault();
              nudgeShellResize("right", 1);
            }
          }}
        />

        <section className="right-rail panel">
          <div className="toolbar">
            <strong>Assistant Activity</strong>
            <span>
              <kbd>tool traces</kbd>
            </span>
          </div>

          <div id="activity">
            {telemetryLoading && telemetryItems.length === 0 && (
              <p>Loading tool activity...</p>
            )}
            {!telemetryLoading && telemetryItems.length === 0 && (
              <p>No tool activity recorded yet.</p>
            )}
            {telemetryItems.map((item, index) => (
              <div
                key={`${item.toolName}-${item.moduleName}-${index}`}
                className="activity-item"
              >
                <div>
                  <strong>{item.toolName}</strong>
                  <span> · {item.moduleName}</span>
                </div>
                <div>
                  calls: {item.invocations} · failures: {item.failures} · p95:{" "}
                  {item.p95DurationMs.toFixed(1)}ms
                </div>
              </div>
            ))}
            {telemetryGeneratedAt && (
              <p className="chat-empty">
                Updated {new Date(telemetryGeneratedAt).toLocaleTimeString()}
              </p>
            )}
          </div>
        </section>

        <div
          className={`shell-resizer shell-resizer-horizontal ${activeResizer === "bottom" ? "is-active" : ""}`}
          role="separator"
          aria-label="Resize assistant chat"
          aria-orientation="horizontal"
          tabIndex={0}
          onPointerDown={(event) => startShellResize("bottom", event)}
          onKeyDown={(event) => {
            if (event.key === "ArrowUp") {
              event.preventDefault();
              nudgeShellResize("bottom", -1);
            }
            if (event.key === "ArrowDown") {
              event.preventDefault();
              nudgeShellResize("bottom", 1);
            }
          }}
        />

        <ChatDock
          chatUnavailable={chatUnavailable}
          apiFailures={apiFailures}
          chatMessages={chatMessages}
          chatLoading={chatLoading}
          chatInput={chatInput}
          retryMessage={retryMessage}
          chatLogRef={chatLogRef}
          chatInputRef={chatInputRef}
          setChatUnavailable={setChatUnavailable}
          setChatInput={setChatInput}
          dismissApiError={dismissApiError}
          retryApiError={retryApiError}
          sendChatMessage={sendChatMessage}
          resetChat={resetChat}
          renderAssistantMarkdown={renderAssistantMarkdown}
        />
      </main>
      <footer className="live-status-bar" role="status" aria-live="polite">
        <div className="status-pill">
          Ollama:
          <span
            className={
              ollamaHealthy === true
                ? "status-ok"
                : ollamaHealthy === false
                  ? "status-error"
                  : "status-unknown"
            }
          >
            {ollamaHealthy === true
              ? "Healthy"
              : ollamaHealthy === false
                ? "Unreachable"
                : "Unknown"}
          </span>
          {ollamaEndpoint && (
            <span className="status-meta">{ollamaEndpoint}</span>
          )}
        </div>
        <div className="status-pill">
          Stream:
          <span className={streamingActive ? "status-ok" : "status-unknown"}>
            {streamingActive ? "Live" : "Idle"}
          </span>
          <span className="status-meta">
            {streamingTokensPerSecond.toFixed(1)} tok/s ({streamingTokenCount}{" "}
            tok)
          </span>
        </div>
      </footer>
    </>
  );
}
