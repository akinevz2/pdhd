import { useCallback, useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { normalizeToolCallMarkup } from "./assistantActions";
import { ChatDock } from "./components/ChatDock";
import { CwdNavigator } from "./components/CwdNavigator";
import { ProjectWindow } from "./components/ProjectWindow";
import { TopMenuAndModals } from "./components/TopMenuAndModals";
import { useHighlightedFiles } from "./hooks/useHighlightedFiles";
import { useMenuPanels } from "./hooks/useMenuPanels";
import {
  ConfigurationMenuButtons,
  ConfigurationModals,
} from "./modules/configuration/ConfigurationMenus";
import { useConfigurationMenus } from "./modules/configuration/useConfigurationMenus";
import type {
  AssistantChatResponse,
  ChatMessage,
  CwdResponse,
  FileContentResponse,
  FsBrowserEntry,
  FsListResponse,
  ProjectSummary as ProjectSummaryType,
  ToolActivityItem,
  ToolTelemetryItem,
  ToolTelemetryResponse,
  TreeNode,
  WindowState,
} from "./types";
import {
  CHAT_TIMEOUT_MS,
  POLL_MS,
  isBrowsableRepoUrl,
  isImagePath,
  isPdfPath,
  openExternalUrl,
} from "./utils";
import {
  emitApiSignal,
  registerApiSignals,
  subscribeSignalErrors,
  type ApiSignalKey,
} from "./signals";

const MARKDOWN_FILE_PATTERN = /\.(md|markdown|mdx)$/i;
const ASSISTANT_ACTION_BLOCK_PATTERN = /```assistant-action\s*([\s\S]*?)```/gi;
const REQUEST_SOURCE_HEADER = "X-Assistant-Request-Source";
const REQUEST_SOURCE_CHAT_INPUT = "chat-input";
const REQUEST_SOURCE_ASSISTANT_ACTION_BUTTON = "assistant-action-button";
const REQUEST_SOURCE_FILE_EXPLORER = "file-explorer";

type AssistantActionPayload = {
  label: string;
  prompt: string;
};

const SIGNALS = {
  TOOL_TELEMETRY_GET: "telemetry:get",
  CWD_GET: "cwd:get",
  CWD_SET: "cwd:set",
  FS_LIST: "fs:list",
  FS_TREE: "fs:tree",
  FS_FILE: "fs:file",
  PROJECTS_LIST: "projects:list",
  CHAT_SEND: "chat:send",
  CHAT_RESET: "chat:reset",
  CHAT_SUMMARIZE_FOLDER: "chat:summarizeFolder",
} as const satisfies Record<string, ApiSignalKey>;

export function App() {
  const [browserPath, setBrowserPath] = useState<string>("");
  const [browserEntries, setBrowserEntries] = useState<FsBrowserEntry[]>([]);
  const [browserLoading, setBrowserLoading] = useState(false);
  const [browserError, setBrowserError] = useState<string | null>(null);
  const [browserRepoUrl, setBrowserRepoUrl] = useState<string | null>(null);

  const [activityItems] = useState<ToolActivityItem[]>([]);

  const [telemetryOpen, setTelemetryOpen] = useState(false);
  const [telemetryItems, setTelemetryItems] = useState<ToolTelemetryItem[]>([]);
  const [telemetryLoading, setTelemetryLoading] = useState(false);
  const [telemetryError, setTelemetryError] = useState<string | null>(null);
  const [telemetrySummary, setTelemetrySummary] = useState<string>("");
  const [telemetryGeneratedAt, setTelemetryGeneratedAt] = useState<string>("");

  const [cwd, setCwd] = useState<string>("");
  const [cwdError, setCwdError] = useState<string | null>(null);
  const [cwdUpdating, setCwdUpdating] = useState(false);

  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState<string>("");
  const [chatLoading, setChatLoading] = useState<boolean>(false);
  const [chatError, setChatError] = useState<string | null>(null);
  const [assistantUnreachable, setAssistantUnreachable] =
    useState<boolean>(false);
  const lastSignalErrorRef = useRef<{ fingerprint: string; timestamp: number } | null>(null);
  const chatLogRef = useRef<HTMLDivElement | null>(null);
  const chatInputRef = useRef<HTMLTextAreaElement | null>(null);
  const [windows, setWindows] = useState<WindowState[]>([]);
  const nextWindowId = useRef(1);
  const nextZ = useRef(10);

  const menuPanels = useMenuPanels();
  const configurationMenus = useConfigurationMenus();

  const highlightedByProject = useHighlightedFiles(activityItems, windows);

  useEffect(() => {
    registerApiSignals([
      [SIGNALS.TOOL_TELEMETRY_GET, { method: "GET", endpoint: "/api/tool-telemetry" }],
      [SIGNALS.CWD_GET, { method: "GET", endpoint: "/api/cwd" }],
      [SIGNALS.CWD_SET, { method: "POST", endpoint: "/api/cwd" }],
      [
        SIGNALS.FS_LIST,
        {
          method: "GET",
          endpoint: (payload: { path: string }) =>
            `/api/fs/list?path=${encodeURIComponent(payload.path)}`,
        },
      ],
      [
        SIGNALS.FS_TREE,
        {
          method: "GET",
          endpoint: (payload: { path: string }) =>
            `/api/fs/tree?path=${encodeURIComponent(payload.path)}`,
        },
      ],
      [
        SIGNALS.FS_FILE,
        {
          method: "GET",
          endpoint: (payload: { path: string }) =>
            `/api/fs/file?path=${encodeURIComponent(payload.path)}`,
        },
      ],
      [SIGNALS.PROJECTS_LIST, { method: "GET", endpoint: "/api/projects" }],
      [SIGNALS.CHAT_SEND, { method: "POST", endpoint: "/api/chat", timeoutMs: CHAT_TIMEOUT_MS }],
      [SIGNALS.CHAT_RESET, { method: "POST", endpoint: "/api/chat/reset" }],
      [
        SIGNALS.CHAT_SUMMARIZE_FOLDER,
        {
          method: "POST",
          endpoint: "/api/chat/summarize-folder",
          timeoutMs: CHAT_TIMEOUT_MS,
        },
      ],
    ]);
  }, []);

  useEffect(() => {
    const unsubscribe = subscribeSignalErrors((signalError) => {
      const fingerprint = `${signalError.signal}:${signalError.message}`;
      const now = Date.now();
      const previous = lastSignalErrorRef.current;

      // Avoid flooding chat with repeated poll failures for the same signal.
      if (
        previous &&
        previous.fingerprint === fingerprint &&
        now - previous.timestamp < 5000
      ) {
        return;
      }

      lastSignalErrorRef.current = { fingerprint, timestamp: now };
      setChatMessages((prev) => [
        ...prev,
        {
          role: "system",
          content: `[signal ${signalError.signal}] ${signalError.message}`,
        },
      ]);
    });

    return unsubscribe;
  }, []);

  const loadTelemetry = useCallback(async () => {
    setTelemetryLoading(true);
    setTelemetryError(null);
    try {
      const data = await emitApiSignal<void, ToolTelemetryResponse>(
        SIGNALS.TOOL_TELEMETRY_GET,
      );
      setTelemetryItems(data.items || []);
      setTelemetrySummary(data.summary || "");
      setTelemetryGeneratedAt(data.generatedAt || "");
    } catch {
      setTelemetryError("Failed to load telemetry.");
    } finally {
      setTelemetryLoading(false);
    }
  }, []);

  const loadCwd = useCallback(async () => {
    try {
      const data = await emitApiSignal<void, CwdResponse>(SIGNALS.CWD_GET);
      setCwd(data.cwd || "");
      setCwdError(null);
    } catch {
      setCwdError("Failed to resolve working folder");
    }
  }, []);

  const loadBrowser = useCallback(
    async (path?: string) => {
      const targetPath = (path || cwd || "").trim();
      if (!targetPath) {
        return;
      }
      setBrowserLoading(true);
      setBrowserError(null);
      try {
        const data = await emitApiSignal<{ path: string }, FsListResponse>(
          SIGNALS.FS_LIST,
          { path: targetPath },
        );
        setBrowserPath(data.path || targetPath);
        setBrowserEntries(data.entries || []);
        setBrowserRepoUrl(data.repoUrl || null);
      } catch {
        setBrowserError("Failed to list directory.");
        setBrowserRepoUrl(null);
      } finally {
        setBrowserLoading(false);
      }
    },
    [cwd],
  );

  const setWorkingFolder = useCallback(
    async (path: string) => {
      setCwdUpdating(true);
      try {
        const data = await emitApiSignal<{ path: string }, CwdResponse>(
          SIGNALS.CWD_SET,
          { path },
        );
        setCwd(data.cwd || "");
        setCwdError(null);
        await loadBrowser(data.cwd || path);
      } catch {
        setCwdError("Failed to update working folder");
      } finally {
        setCwdUpdating(false);
      }
    },
    [loadBrowser],
  );

  useEffect(() => {
    loadCwd();
    const timer = window.setInterval(() => {
      loadCwd().catch(() => {
        // polling errors are non-fatal
      });
    }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [loadCwd]);

  useEffect(() => {
    if (!cwd) {
      return;
    }
    loadBrowser(cwd).catch(() => {
      // surfaced by browser state
    });
  }, [cwd, loadBrowser]);

  useEffect(() => {
    if (!telemetryOpen) {
      return;
    }
    loadTelemetry().catch(() => {
      // surfaced by telemetry state
    });
  }, [telemetryOpen, loadTelemetry]);

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

  const closeWindow = useCallback((id: number) => {
    setWindows((prev) => prev.filter((w) => w.id !== id));
  }, []);

  const updateWindow = useCallback(
    (id: number, patch: Partial<WindowState>) => {
      setWindows((prev) =>
        prev.map((w) => (w.id === id ? { ...w, ...patch } : w)),
      );
    },
    [],
  );

  const loadTreeForWindow = useCallback(
    async (windowId: number, directory: string) => {
      try {
        const tree = await emitApiSignal<{ path: string }, TreeNode>(
          SIGNALS.FS_TREE,
          { path: directory },
        );
        updateWindow(windowId, {
          tree,
          treeLoading: false,
          treeError: undefined,
        });
      } catch {
        updateWindow(windowId, {
          treeLoading: false,
          treeError: "Failed to load project tree.",
        });
      }
    },
    [updateWindow],
  );

  const openInCanvas = useCallback(
    async (path: string) => {
      const target = path || cwd;
      if (!target) return;
      const projects = await emitApiSignal<void, ProjectSummaryType[]>(
        SIGNALS.PROJECTS_LIST,
      );
      const match = projects
        .filter(
          (p) =>
            target === p.directory ||
            target.startsWith(p.directory + "/") ||
            target.startsWith(p.directory + "\\"),
        )
        .sort((a, b) => b.directory.length - a.directory.length)[0];
      if (!match) return;
      const existing = windows.find((w) => w.project.id === match.id);
      if (existing) {
        focusWindow(existing.id);
        setWorkingFolder(match.directory).catch(() => {});
        return;
      }
      const winId = ++nextWindowId.current;
      setWindows((prev) => [
        ...prev,
        {
          id: winId,
          project: match,
          treeLoading: true,
          x: 60 + (winId % 6) * 30,
          y: 40 + (winId % 6) * 30,
          z: ++nextZ.current,
        },
      ]);
      await loadTreeForWindow(winId, match.directory);
    },
    [cwd, windows, focusWindow, loadTreeForWindow],
  );


  const openFile = useCallback(
    async (
      windowId: number,
      projectDirectory: string,
      relativePath: string,
    ) => {
      if (isImagePath(relativePath) || isPdfPath(relativePath)) {
        updateWindow(windowId, {
          selectedFilePath: relativePath,
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
        fileLoading: true,
        fileLoadingFolderSummary: false,
        fileContentMarkdown: MARKDOWN_FILE_PATTERN.test(relativePath),
        fileError: undefined,
      });
      const absolutePath =
        projectDirectory.replace(/\\/g, "/") + "/" + relativePath;
      try {
        const file = await emitApiSignal<{ path: string }, FileContentResponse>(
          SIGNALS.FS_FILE,
          { path: absolutePath },
        );
        updateWindow(windowId, {
          fileLoading: false,
          fileLoadingFolderSummary: false,
          fileContent: file.content,
          fileContentMarkdown: MARKDOWN_FILE_PATTERN.test(relativePath),
          fileError: undefined,
        });
      } catch {
        updateWindow(windowId, {
          fileLoading: false,
          fileLoadingFolderSummary: false,
          fileContentMarkdown: false,
          fileError: "Failed to read file.",
        });
      }
    },
    [updateWindow],
  );

  const openFileInCanvas = useCallback(
    async (filePath: string) => {
      if (!filePath) return;
      const projects = await emitApiSignal<void, ProjectSummaryType[]>(
        SIGNALS.PROJECTS_LIST,
      );
      const match = projects
        .filter(
          (p) =>
            filePath.startsWith(p.directory + "/") ||
            filePath.startsWith(p.directory + "\\"),
        )
        .sort((a, b) => b.directory.length - a.directory.length)[0];
      if (!match) return;
      const relativePath = filePath
        .slice(match.directory.length)
        .replace(/^[\/\\]/, "");
      let winId: number;
      const existing = windows.find((w) => w.project.id === match.id);
      if (existing) {
        focusWindow(existing.id);
        setWorkingFolder(match.directory).catch(() => {});
        winId = existing.id;
      } else {
        winId = ++nextWindowId.current;
        setWindows((prev) => [
          ...prev,
          {
            id: winId,
            project: match,
            treeLoading: true,
            x: 60 + (winId % 6) * 30,
            y: 40 + (winId % 6) * 30,
            z: ++nextZ.current,
          },
        ]);
        await loadTreeForWindow(winId, match.directory);
      }
      await openFile(winId, match.directory, relativePath);
    },
    [cwd, windows, focusWindow, setWorkingFolder, loadTreeForWindow, openFile],
  );

  const openFolderSummary = useCallback(
    async (
      windowId: number,
      projectDirectory: string,
      relativePath: string,
    ) => {
      const safeRelativePath = relativePath || "";
      const folderPath = safeRelativePath
        ? `${projectDirectory.replace(/\\/g, "/")}/${safeRelativePath}`
        : projectDirectory;

      updateWindow(windowId, {
        selectedFilePath: safeRelativePath || ".",
        fileLoading: true,
        fileLoadingFolderSummary: true,
        fileContentMarkdown: true,
        fileError: undefined,
      });
      setChatError(null);
      setChatLoading(true);

      try {
        setChatMessages((prev) => [
          ...prev,
          { role: "user", content: `Summarise folder: ${folderPath}` },
        ]);

        const MAX_ATTEMPTS = 3;
        let result: AssistantChatResponse | undefined;
        let lastErr: unknown;
        for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
          if (attempt > 0) {
            await new Promise((r) => setTimeout(r, 1000 * 2 ** (attempt - 1)));
          }
          try {
            result = await emitApiSignal<{ path: string }, AssistantChatResponse>(
              SIGNALS.CHAT_SUMMARIZE_FOLDER,
              { path: folderPath },
              {
                headers: {
                  [REQUEST_SOURCE_HEADER]: REQUEST_SOURCE_FILE_EXPLORER,
                },
              },
            );
            break;
          } catch (err) {
            lastErr = err;
            // don't retry on 4xx client errors
            if (err instanceof Error && /^4\d\d /.test(err.message)) break;
          }
        }
        if (!result) throw lastErr;
        const reply = result.reply || "No summary returned.";

        setChatMessages((prev) => [
          ...prev,
          { role: "assistant", content: reply },
        ]);

        setAssistantUnreachable(false);
        updateWindow(windowId, {
          fileLoading: false,
          fileLoadingFolderSummary: false,
          fileError: undefined,
          fileContent: reply,
          fileContentMarkdown: true,
        });
      } catch (err) {
        const detail = err instanceof Error ? err.message : "Unknown error";
        setAssistantUnreachable(true);
        setChatError(`Failed to summarize folder contents: ${detail}`);
        updateWindow(windowId, {
          fileLoading: false,
          fileLoadingFolderSummary: false,
          fileContentMarkdown: false,
          fileError: `Failed to summarize folder contents: ${detail}`,
        });
      } finally {
        setChatLoading(false);
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
      source: string = REQUEST_SOURCE_CHAT_INPUT,
    ) => {
      const message = (overrideMessage ?? chatInput).trim();
      if (!message || chatLoading) {
        return;
      }

      setChatError(null);
      setChatLoading(true);
      if (!overrideMessage) setChatInput("");
      setChatMessages((prev) => [...prev, { role: "user", content: message }]);
      setRetryMessage(null);
      setChatError(null);
      // Refocus the chat input for accessibility
      setTimeout(() => {
        if (chatInputRef.current) chatInputRef.current.focus();
      }, 0);

      try {
        const result = await emitApiSignal<{ message: string }, AssistantChatResponse>(
          SIGNALS.CHAT_SEND,
          { message },
          {
            timeoutMs: CHAT_TIMEOUT_MS,
            headers: { [REQUEST_SOURCE_HEADER]: source },
          },
        );
        setChatMessages((prev) => [
          ...prev,
          { role: "assistant", content: result.reply || "" },
        ]);
        setAssistantUnreachable(false);
        setRetryMessage(null);
      } catch (err) {
        const detail = err instanceof Error ? err.message : "Unknown error";
        setAssistantUnreachable(true);
        setChatError(detail);
        setRetryMessage(message);
      } finally {
        setChatLoading(false);
      }
    },
    [chatInput, chatLoading],
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
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{normalizedContent}</ReactMarkdown>
        );
      }
      return parts;
    },
    [chatLoading, parseAssistantActionPayload, sendChatMessage],
  );

  const resetChat = useCallback(async () => {
    try {
      await emitApiSignal<Record<string, never>, unknown>(SIGNALS.CHAT_RESET, {});
      setChatMessages([]);
      setChatError(null);
    } catch {
      setChatError("Failed to reset assistant conversation.");
    }
  }, []);

  useEffect(() => {
    if (chatLogRef.current) {
      chatLogRef.current.scrollTop = chatLogRef.current.scrollHeight;
    }
  }, [chatMessages, chatLoading]);

  return (
    <>
      <main id="app-shell">
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
          telemetryError={telemetryError}
          telemetrySummary={telemetrySummary}
          telemetryGeneratedAt={telemetryGeneratedAt}
          onRefreshTelemetry={loadTelemetry}
        />
        <header className="cwd-bar panel">
          <strong>Working Folder</strong>
          <CwdNavigator
            cwd={cwd}
            cwdError={cwdError}
            onNavigate={setWorkingFolder}
          />
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
              {!browserLoading && browserError && <p>{browserError}</p>}
              {!browserLoading &&
                !browserError &&
                browserEntries.length === 0 && <p>Folder is empty.</p>}
              {!browserLoading && !browserError && (
                <>
                  {/* Parent directory entry */}
                  <div key=".." className="browser-row">
                    <button
                      className="browser-row-main"
                      onClick={() => {
                        setWorkingFolder("..").catch(() => {});
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
                    <div key={entry.path} className="browser-row">
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
                              openInCanvas(entry.path).catch(() => {});
                            }}
                            title={`Open ${entry.name} in explorer`}
                            aria-label={`Open ${entry.name} in explorer`}
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
                              openFileInCanvas(entry.path).catch(() => {});
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
                          <button
                            className="browser-row-action explore-button"
                            onClick={() => {
                              openFileInCanvas(entry.path).catch(() => {});
                            }}
                            title={`Open ${entry.name} in explorer`}
                            aria-label={`Open ${entry.name} in explorer`}
                          >
                            →
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

        <section className="window-host panel">
          {windows.map((win) => (
            <ProjectWindow
              key={win.id}
              windowState={win}
              highlighted={
                highlightedByProject.get(win.project.directory) ||
                new Set<string>()
              }
              onClose={() => closeWindow(win.id)}
              onFocus={() => focusWindow(win.id)}
              onMove={(x, y) => moveWindow(win.id, x, y)}
              onOpenFile={(path) =>
                openFile(win.id, win.project.directory, path)
              }
              onOpenFolderSummary={(path) =>
                openFolderSummary(win.id, win.project.directory, path)
              }
              onAssistantAction={(prompt) => {
                sendChatMessage(prompt, REQUEST_SOURCE_ASSISTANT_ACTION_BUTTON).catch(() => {
                  // handled in callback
                });
              }}
              assistantActionDisabled={chatLoading}
            />
          ))}
        </section>

        <section className="right-rail panel">
          <div className="toolbar">
            <strong>Assistant Activity</strong>
            <span>
              <kbd>tool traces</kbd>
            </span>
          </div>

          <div id="activity">
            <p>Tool activity feed has been removed from the backend.</p>
          </div>
        </section>

        <ChatDock
          assistantUnreachable={assistantUnreachable}
          chatMessages={chatMessages}
          chatLoading={chatLoading}
          chatInput={chatInput}
          chatError={chatError}
          retryMessage={retryMessage}
          chatLogRef={chatLogRef}
          chatInputRef={chatInputRef}
          setAssistantUnreachable={setAssistantUnreachable}
          setChatInput={setChatInput}
          sendChatMessage={sendChatMessage}
          resetChat={resetChat}
          renderAssistantMarkdown={renderAssistantMarkdown}
        />
      </main>
    </>
  );
}
