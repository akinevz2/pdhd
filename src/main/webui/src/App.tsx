import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

type ProjectSummary = {
  id: number;
  directory: string;
  hasGitRepository: boolean;
  githubName?: string | null;
  githubDescription?: string | null;
};

type TreeNode = {
  name: string;
  relativePath: string;
  directory: boolean;
  children: TreeNode[];
};

type ToolActivityItem = {
  timestamp: string;
  toolName: string;
  argumentsJson: string;
  result: string;
  requestedFiles: string[];
};

type ToolActivityResponse = {
  items: ToolActivityItem[];
};

type FileContentResponse = {
  projectDirectory: string;
  filePath: string;
  content: string;
};

type CwdResponse = {
  cwd: string;
};

type AssistantChatResponse = {
  reply: string;
};

type ChatMessage = {
  role: "user" | "assistant" | "system";
  content: string;
};

type WindowState = {
  id: number;
  project: ProjectSummary;
  treeLoading: boolean;
  treeError?: string;
  tree?: TreeNode;
  selectedFilePath?: string;
  fileContent?: string;
  fileLoading?: boolean;
  fileError?: string;
  x: number;
  y: number;
  z: number;
};

const POLL_MS = 2000;
const API_TIMEOUT_MS = 10000;

export function App() {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [projectsLoading, setProjectsLoading] = useState(true);
  const [projectsError, setProjectsError] = useState<string | null>(null);
  const [hiddenProjectIds, setHiddenProjectIds] = useState<Set<number>>(
    new Set(),
  );

  const dismissProject = useCallback((id: number) => {
    setHiddenProjectIds((prev) => new Set(prev).add(id));
  }, []);

  const [activityItems, setActivityItems] = useState<ToolActivityItem[]>([]);
  const [activityError, setActivityError] = useState<string | null>(null);

  const [cwd, setCwd] = useState<string>("");
  const [cwdError, setCwdError] = useState<string | null>(null);

  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState<string>("");
  const [chatLoading, setChatLoading] = useState<boolean>(false);
  const [chatError, setChatError] = useState<string | null>(null);
  const [assistantUnreachable, setAssistantUnreachable] =
    useState<boolean>(false);
  const chatLogRef = useRef<HTMLDivElement | null>(null);

  const [windows, setWindows] = useState<WindowState[]>([]);
  const nextWindowId = useRef(1);
  const nextZ = useRef(10);

  const highlightedByProject = useMemo(() => {
    const out = new Map<string, Set<string>>();
    const openProjects = new Set(windows.map((w) => w.project.directory));

    for (const event of activityItems) {
      for (const requested of event.requestedFiles || []) {
        const normalized = normalize(requested);
        for (const projectDirectory of openProjects) {
          if (!out.has(projectDirectory)) {
            out.set(projectDirectory, new Set<string>());
          }
          out.get(projectDirectory)?.add(normalized);
        }
      }
    }
    return out;
  }, [activityItems, windows]);

  const loadProjects = useCallback(async () => {
    setProjectsLoading(true);
    setProjectsError(null);
    try {
      const data = await api<ProjectSummary[]>("/api/projects");
      setProjects(data);
    } catch (e) {
      setProjectsError("Failed to load projects.");
    } finally {
      setProjectsLoading(false);
    }
  }, []);

  const loadActivity = useCallback(async (quiet = true) => {
    try {
      const data = await api<ToolActivityResponse>(
        "/api/tool-activity?limit=80",
      );
      setActivityItems(data.items || []);
      setActivityError(null);
    } catch (e) {
      if (!quiet) {
        setActivityError("Failed to load tool activity.");
      }
    }
  }, []);

  const loadCwd = useCallback(async () => {
    try {
      const data = await api<CwdResponse>("/api/cwd");
      setCwd(data.cwd || "");
      setCwdError(null);
    } catch (e) {
      setCwdError("Failed to resolve working folder");
    }
  }, []);

  const setWorkingFolder = useCallback(async (path: string) => {
    try {
      const data = await apiPost<{ path: string }, CwdResponse>("/api/cwd", {
        path,
      });
      setCwd(data.cwd || "");
      setCwdError(null);
    } catch (e) {
      setCwdError("Failed to update working folder");
    }
  }, []);

  useEffect(() => {
    loadProjects();
    loadActivity(false);
    loadCwd();
    const timer = window.setInterval(() => {
      loadActivity(true).catch(() => {
        // polling errors are non-fatal
      });
      loadCwd().catch(() => {
        // polling errors are non-fatal
      });
    }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [loadActivity, loadProjects, loadCwd]);

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
    async (windowId: number, projectId: number) => {
      try {
        const tree = await api<TreeNode>(`/api/projects/${projectId}/tree`);
        updateWindow(windowId, {
          tree,
          treeLoading: false,
          treeError: undefined,
        });
      } catch (e) {
        updateWindow(windowId, {
          treeLoading: false,
          treeError: "Failed to load project tree.",
        });
      }
    },
    [updateWindow],
  );

  const openProjectWindow = useCallback(
    async (project: ProjectSummary) => {
      await setWorkingFolder(project.directory);
      const id = nextWindowId.current++;
      const win: WindowState = {
        id,
        project,
        treeLoading: true,
        x: 80 + Math.floor(Math.random() * 140),
        y: 40 + Math.floor(Math.random() * 120),
        z: ++nextZ.current,
      };
      setWindows((prev) => [...prev, win]);
      loadTreeForWindow(id, project.id).catch(() => {
        // handled in updater
      });
      loadProjects().catch(() => {
        // handled in loader state
      });
    },
    [loadProjects, loadTreeForWindow, setWorkingFolder],
  );

  const openFile = useCallback(
    async (windowId: number, projectId: number, path: string) => {
      if (isImagePath(path)) {
        updateWindow(windowId, {
          selectedFilePath: path,
          fileLoading: false,
          fileContent: undefined,
          fileError: undefined,
        });
        return;
      }

      updateWindow(windowId, {
        selectedFilePath: path,
        fileLoading: true,
        fileError: undefined,
      });
      try {
        const file = await api<FileContentResponse>(
          `/api/projects/${projectId}/file?path=${encodeURIComponent(path)}`,
        );
        updateWindow(windowId, {
          fileLoading: false,
          fileContent: file.content,
          fileError: undefined,
        });
      } catch (e) {
        updateWindow(windowId, {
          fileLoading: false,
          fileError: "Failed to read file.",
        });
      }
    },
    [updateWindow],
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
        fileError: undefined,
      });
      setChatError(null);
      setChatLoading(true);

      try {
        const message = [
          `Summarise the contents of this folder: ${folderPath}`,
          "Use tools to inspect the folder and files before answering.",
          "Return a concise but useful 'file contents' summary for a developer.",
        ].join(" ");

        setChatMessages((prev) => [
          ...prev,
          { role: "user", content: `Summarise folder: ${folderPath}` },
        ]);

        const result = await apiPost<
          { message: string },
          AssistantChatResponse
        >("/api/chat/oneshot", { message });
        const reply = result.reply || "No summary returned.";

        setChatMessages((prev) => [
          ...prev,
          { role: "assistant", content: reply },
        ]);

        setAssistantUnreachable(false);
        updateWindow(windowId, {
          fileLoading: false,
          fileError: undefined,
          fileContent: reply,
        });
      } catch (e) {
        setAssistantUnreachable(true);
        setChatError("Failed to summarize folder contents.");
        setChatMessages((prev) => {
          const updated = [...prev];
          const lastIdx = updated.length - 1;
          if (
            updated[lastIdx]?.role === "assistant" &&
            !updated[lastIdx].content
          ) {
            updated[lastIdx] = {
              role: "system",
              content: `Folder summary failed for: ${folderPath}`,
            };
          } else {
            updated.push({
              role: "system",
              content: `Folder summary failed for: ${folderPath}`,
            });
          }
          return updated;
        });
        updateWindow(windowId, {
          fileLoading: false,
          fileError: "Failed to summarize folder contents.",
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

  const sendChatMessage = useCallback(async () => {
    const message = chatInput.trim();
    if (!message || chatLoading) {
      return;
    }

    setChatError(null);
    setChatLoading(true);
    setChatInput("");
    setChatMessages((prev) => [...prev, { role: "user", content: message }]);

    try {
      const result = await apiPost<{ message: string }, AssistantChatResponse>(
        "/api/chat",
        { message },
      );
      setChatMessages((prev) => [
        ...prev,
        { role: "assistant", content: result.reply || "" },
      ]);
      setAssistantUnreachable(false);
    } catch (e) {
      setAssistantUnreachable(true);
      setChatError("Failed to contact assistant.");
      setChatMessages((prev) => [
        ...prev,
        {
          role: "system",
          content: "Assistant request failed. Check Ollama configuration.",
        },
      ]);
    } finally {
      setChatLoading(false);
    }
  }, [chatInput, chatLoading]);

  const resetChat = useCallback(async () => {
    try {
      await apiPost<{}, unknown>("/api/chat/reset", {});
      setChatMessages([]);
      setChatError(null);
    } catch (e) {
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
            <strong>Projects</strong>
            <button onClick={() => loadProjects()}>Refresh</button>
          </div>

          <div id="projectList">
            {projectsLoading && <p>Loading projects...</p>}
            {!projectsLoading && projectsError && <p>{projectsError}</p>}
            {!projectsLoading && !projectsError && projects.length === 0 && (
              <p>No projects discovered yet.</p>
            )}
            {!projectsLoading &&
              !projectsError &&
              projects
                .filter((p) => !hiddenProjectIds.has(p.id))
                .map((project) => (
                  <div key={project.id} className="window panel project-card">
                    <div className="title-bar">
                      <div className="title-bar-text">
                        {project.githubName || project.directory}
                      </div>
                      <div className="title-bar-controls">
                        <button
                          aria-label="Close"
                          onClick={() => dismissProject(project.id)}
                        />
                      </div>
                    </div>
                    <div className="window-body project-body">
                      <p>{project.directory}</p>
                      <button
                        onClick={() => {
                          openProjectWindow(project).catch(() => {
                            // handled in setWorkingFolder
                          });
                        }}
                      >
                        Open Explorer
                      </button>
                    </div>
                  </div>
                ))}
          </div>
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
              onOpenFile={(path) => openFile(win.id, win.project.id, path)}
              onOpenFolderSummary={(path) =>
                openFolderSummary(win.id, win.project.directory, path)
              }
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
            {activityError && <p>{activityError}</p>}
            {!activityError && activityItems.length === 0 && (
              <p>No tool activity yet.</p>
            )}
            {!activityError &&
              activityItems
                .slice()
                .reverse()
                .slice(0, 40)
                .map((event, index) => (
                  <div
                    className="activity-item"
                    key={`${event.timestamp}-${event.toolName}-${index}`}
                  >
                    <div>
                      <strong>{event.toolName}</strong>{" "}
                      <small>
                        {new Date(event.timestamp).toLocaleTimeString()}
                      </small>
                    </div>
                    <div>
                      {(event.requestedFiles || []).join(", ") ||
                        "no file paths"}
                    </div>
                  </div>
                ))}
          </div>
        </section>

        <section className="chat-dock panel">
          <div className="toolbar">
            <strong>Assistant Chat</strong>
            <button onClick={() => resetChat()} disabled={chatLoading}>
              Reset
            </button>
          </div>

          {assistantUnreachable && (
            <div className="assistant-unreachable-notice">
              <span>
                ⚠ Assistant is unreachable — check that Ollama is running and
                the model is loaded.
              </span>
              <button
                className="notice-dismiss"
                onClick={() => setAssistantUnreachable(false)}
                aria-label="Dismiss"
              >
                ✕
              </button>
            </div>
          )}

          <div className="chat-log" ref={chatLogRef}>
            {chatMessages.length === 0 && (
              <p className="chat-empty">
                Ask the assistant about this project.
              </p>
            )}
            {chatMessages.map((entry, idx) => (
              <div
                key={`${entry.role}-${idx}`}
                className={`chat-row ${entry.role}`}
              >
                <strong>
                  {entry.role === "user"
                    ? "You"
                    : entry.role === "assistant"
                      ? "Assistant"
                      : "System"}
                </strong>
                {entry.role === "assistant" ? (
                  <div className="chat-message-markdown">
                    {entry.content ? (
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {entry.content}
                      </ReactMarkdown>
                    ) : chatLoading ? (
                      "▊"
                    ) : (
                      ""
                    )}
                  </div>
                ) : (
                  <span>{entry.content}</span>
                )}
              </div>
            ))}
            {chatLoading && (
              <div className="chat-row assistant">Assistant is thinking...</div>
            )}
          </div>

          <div className="chat-compose">
            <textarea
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
              placeholder="Type a message..."
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  sendChatMessage().catch(() => {
                    // handled in callback
                  });
                }
              }}
              disabled={chatLoading}
            />
            <button
              onClick={() => {
                sendChatMessage().catch(() => {
                  // handled in callback
                });
              }}
              disabled={chatLoading || !chatInput.trim()}
            >
              Send
            </button>
          </div>
          {chatError && <p className="chat-error">{chatError}</p>}
        </section>
      </main>
    </>
  );
}

type ProjectWindowProps = {
  windowState: WindowState;
  highlighted: Set<string>;
  onClose: () => void;
  onFocus: () => void;
  onMove: (x: number, y: number) => void;
  onOpenFile: (path: string) => void;
  onOpenFolderSummary: (path: string) => void;
};

function ProjectWindow({
  windowState,
  highlighted,
  onClose,
  onFocus,
  onMove,
  onOpenFile,
  onOpenFolderSummary,
}: ProjectWindowProps) {
  const dragRef = useRef<{
    active: boolean;
    startX: number;
    startY: number;
    originX: number;
    originY: number;
  }>({
    active: false,
    startX: 0,
    startY: 0,
    originX: 0,
    originY: 0,
  });

  useEffect(() => {
    const onMouseMove = (e: MouseEvent) => {
      if (!dragRef.current.active) {
        return;
      }
      const x = dragRef.current.originX + (e.clientX - dragRef.current.startX);
      const y = dragRef.current.originY + (e.clientY - dragRef.current.startY);
      onMove(x, y);
    };

    const onMouseUp = () => {
      dragRef.current.active = false;
    };

    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
    };
  }, [onMove]);

  const beginDrag = (e: React.MouseEvent) => {
    onFocus();
    dragRef.current.active = true;
    dragRef.current.startX = e.clientX;
    dragRef.current.startY = e.clientY;
    dragRef.current.originX = windowState.x;
    dragRef.current.originY = windowState.y;
  };

  return (
    <section
      className="window-card window"
      style={{ left: windowState.x, top: windowState.y, zIndex: windowState.z }}
      onMouseDown={() => onFocus()}
    >
      <div className="title-bar win-title" onMouseDown={beginDrag}>
        <div className="title-bar-text">{windowState.project.directory}</div>
        <div className="title-bar-controls">
          <button aria-label="Close" onClick={onClose} />
        </div>
      </div>

      <div className="win-body">
        <aside className="tree-pane">
          {windowState.treeLoading && <div>Loading tree...</div>}
          {!windowState.treeLoading && windowState.treeError && (
            <div>{windowState.treeError}</div>
          )}
          {!windowState.treeLoading &&
            !windowState.treeError &&
            windowState.tree && (
              <TreeView
                root={windowState.tree}
                highlighted={highlighted}
                onOpenFile={(path) => onOpenFile(path)}
                onOpenFolderSummary={(path) => onOpenFolderSummary(path)}
              />
            )}
        </aside>

        <article className="file-pane">
          {windowState.fileLoading && <pre>Loading file...</pre>}
          {!windowState.fileLoading && windowState.fileError && (
            <pre>{windowState.fileError}</pre>
          )}
          {!windowState.fileLoading &&
            !windowState.fileError &&
            windowState.selectedFilePath &&
            isImagePath(windowState.selectedFilePath) && (
              <div className="image-preview-wrap">
                <img
                  className="image-preview"
                  src={rawImageUrl(
                    windowState.project.id,
                    windowState.selectedFilePath,
                  )}
                  alt={windowState.selectedFilePath}
                />
                <div className="image-caption">
                  {windowState.selectedFilePath}
                </div>
              </div>
            )}
          {!windowState.fileLoading &&
            !windowState.fileError &&
            (!windowState.selectedFilePath ||
              !isImagePath(windowState.selectedFilePath)) && (
              <pre>
                {windowState.fileContent || "Select a file to view content."}
              </pre>
            )}
        </article>
      </div>
    </section>
  );
}

type TreeViewProps = {
  root: TreeNode;
  highlighted: Set<string>;
  onOpenFile: (path: string) => void;
  onOpenFolderSummary: (path: string) => void;
};

function TreeView({
  root,
  highlighted,
  onOpenFile,
  onOpenFolderSummary,
}: TreeViewProps) {
  const rows: React.ReactNode[] = [];

  const walk = (node: TreeNode, depth: number) => {
    if (!node.relativePath && depth === 0) {
      for (const child of node.children || []) {
        walk(child, depth + 1);
      }
      return;
    }

    const isHighlighted = highlighted.has(normalize(node.relativePath || ""));
    rows.push(
      <div
        key={node.relativePath}
        className={`file-node ${isHighlighted ? "highlight" : ""}`}
        style={{ marginLeft: `${Math.max(0, depth - 1) * 12}px` }}
        onClick={
          node.directory
            ? () => onOpenFolderSummary(node.relativePath)
            : () => onOpenFile(node.relativePath)
        }
      >
        <span className="node-entry">
          <span
            className={`node-icon ${node.directory ? "folder" : "file"}`}
            aria-hidden="true"
          >
            {node.directory ? "▸" : "●"}
          </span>
          <span className="node-label">{node.name}</span>
        </span>
      </div>,
    );

    if (node.directory) {
      for (const child of node.children || []) {
        walk(child, depth + 1);
      }
    }
  };

  walk(root, 0);
  return <div className="tree-list">{rows}</div>;
}

function CwdNavigator({
  cwd,
  cwdError,
  onNavigate,
}: {
  cwd: string;
  cwdError: string | null;
  onNavigate: (path: string) => Promise<void>;
}) {
  const [editing, setEditing] = useState(false);
  const [input, setInput] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [highlighted, setHighlighted] = useState(-1);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (editing && inputRef.current) {
      inputRef.current.select();
    }
  }, [editing]);

  useEffect(() => {
    if (!editing || !input) {
      setSuggestions([]);
      return;
    }
    const id = window.setTimeout(() => {
      api<{ dirs: string[] }>(`/api/fs/dirs?path=${encodeURIComponent(input)}`)
        .then((data) => setSuggestions(data.dirs || []))
        .catch(() => setSuggestions([]));
    }, 150);
    return () => window.clearTimeout(id);
  }, [input, editing]);

  useEffect(() => {
    if (!editing) return;
    const handler = (e: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setEditing(false);
        setSuggestions([]);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [editing]);

  const commit = (path: string) => {
    setEditing(false);
    setSuggestions([]);
    const trimmed = path.trim();
    if (trimmed && trimmed !== cwd) {
      onNavigate(trimmed).catch(() => {});
    }
  };

  if (!editing) {
    return (
      <span
        className="cwd-path cwd-path-clickable"
        onClick={() => {
          setInput(cwd);
          setHighlighted(-1);
          setEditing(true);
        }}
        title="Click to change working folder"
      >
        {cwdError || cwd || "Loading..."}
      </span>
    );
  }

  return (
    <div ref={containerRef} className="cwd-editor">
      <input
        ref={inputRef}
        className="cwd-input"
        value={input}
        onChange={(e) => {
          setInput(e.target.value);
          setHighlighted(-1);
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            const target =
              highlighted >= 0 && highlighted < suggestions.length
                ? suggestions[highlighted]
                : input;
            commit(target);
          } else if (e.key === "Escape") {
            setEditing(false);
            setSuggestions([]);
          } else if (e.key === "ArrowDown") {
            e.preventDefault();
            setHighlighted((h) => Math.min(h + 1, suggestions.length - 1));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setHighlighted((h) => Math.max(h - 1, -1));
          } else if (e.key === "Tab") {
            e.preventDefault();
            const pick =
              highlighted >= 0 && highlighted < suggestions.length
                ? suggestions[highlighted]
                : suggestions.length > 0
                  ? suggestions[0]
                  : null;
            if (pick) {
              setInput(pick + "/");
              setHighlighted(-1);
            }
          }
        }}
      />
      {suggestions.length > 0 && (
        <ul className="cwd-suggestions">
          {suggestions.map((s, i) => (
            <li
              key={s}
              className={`cwd-suggestion${i === highlighted ? " active" : ""}`}
              onMouseDown={(e) => {
                e.preventDefault();
                commit(s);
              }}
              onMouseEnter={() => setHighlighted(i)}
            >
              {s}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

async function api<T>(url: string): Promise<T> {
  return await fetchJson<T>(url, { method: "GET" });
}

async function apiPost<TReq, TRes>(url: string, body: TReq): Promise<TRes> {
  const response = await fetchWithTimeout(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  if (response.status === 204) {
    return {} as TRes;
  }
  return (await response.json()) as TRes;
}

async function fetchJson<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetchWithTimeout(url, init);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return (await response.json()) as T;
}

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), API_TIMEOUT_MS);

  try {
    return await fetch(url, {
      ...init,
      signal: controller.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error(`Request timed out after ${API_TIMEOUT_MS}ms`);
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
  }
}

function normalize(path: string): string {
  return path.replace(/\\/g, "/").replace(/^\/+/, "").toLowerCase();
}

function isImagePath(path: string): boolean {
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(path);
}

function rawImageUrl(projectId: number, relativePath: string): string {
  return `/api/projects/${projectId}/file/raw?path=${encodeURIComponent(relativePath)}`;
}
