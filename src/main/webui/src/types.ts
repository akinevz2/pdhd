/** Raw project entity returned by the backend project API. */
export type ProjectFolderEntity = {
  id: number;
  directory: string;
  loaded: boolean;
  gitRepository?: unknown | null;
  githubRepository?: unknown | null;
};

export type ProjectRemoteUrlResponse = {
  remoteUrl?: string | null;
};

/** Summary of a discovered software project returned by the API. */
export type ProjectSummary = {
  id: number;
  directory: string;
  hasGitRepository: boolean;
  hasGithubRepository: boolean;
  loaded: boolean;
  /** Present only on the register response when the directory has no .git folder. */
  warning?: string;
};

/** Runtime status of the Ollama backend (local vs. Testcontainers fallback). */
export type OllamaRuntimeStatus = {
  runtimeEndpoint: string;
  runtimeProvider: "EXTERNAL" | "INTERNAL";
  healthy: boolean;
};

/** Incremental progress event emitted during an Ollama model pull. */
export type PullProgressStatus = {
  status: string;
  digest: string | null;
  total: number;
  completed: number;
};

/** A single tool-call trace emitted by the AI assistant. */
export type ToolActivityItem = {
  timestamp: string;
  toolName: string;
  argumentsJson: string;
  result: string;
  requestedFiles: string[];
};

/** A single row from the typed telemetry endpoint. */
export type ToolTelemetryItem = {
  toolName: string;
  moduleName: string;
  invocations: number;
  failures: number;
  argumentValidationFailures: number;
  averageDurationMs: number;
  p50DurationMs: number;
  p95DurationMs: number;
  errorClasses: Record<string, number>;
};

/** Wrapper returned by the typed telemetry endpoint. */
export type ToolTelemetryResponse = {
  schemaVersion: string;
  generatedAt: string;
  summary: string;
  items: ToolTelemetryItem[];
};

/** Response from file-content API endpoint. */
export type FileContentResponse = {
  filePath: string;
  content?: string;
  mimeType: string;
  language: string;
  requiresPdfViewer: boolean;
  requiresImageViewer: boolean;
  requiresMarkdownViewer: boolean;
};

/** Response from the CWD API endpoints. */
type FsBrowserEntryBase = {
  name: string;
  path: string;
};

/** A file row in the filesystem browser. */
export type FsBrowserFile = FsBrowserEntryBase & {
  directory: false;
  uuid: string;
};

/** A folder row in the filesystem browser. */
export type FsBrowserFolder = FsBrowserEntryBase & {
  directory: true;
  uuid: string;
  repoUrl?: string | null;
};

/** A single filesystem row in the simple left-pane browser. */
export type FsBrowserEntry = FsBrowserFile | FsBrowserFolder;

/** Response from GET /api/workspace (current working directory view). */
export type WorkspaceResponse = {
  path: string;
  repoUrl?: string | null;
  entries: FsBrowserEntry[];
};

/** Response from GET /api/project/{uuid}/browse. */
export type BrowseResponse = {
  parentUuid: string;
  entries: FsBrowserEntry[];
};

export type FolderSummaryResponse = {
  folderPath: string;
  summary: string;
  analysedFiles: number;
  skippedFiles: number;
  updatedAt?: string | null;
  fallbackReason?: string | null;
  persisted: boolean;
};

export type FolderSubsummaryItem = {
  targetPath: string;
  purpose: string;
  updatedAt?: string | null;
};

export type FolderSubsummaryResponse = {
  folderPath: string;
  count: number;
  items: FolderSubsummaryItem[];
};

export type FolderSummaryStatusResponse = {
  folderPath: string;
  exists: boolean;
  updatedAt?: string | null;
};

/** Response from the assistant chat API. */
export type AssistantChatResponse = {
  reply: string;
};

/** Persisted Ollama configuration used by the frontend menus. */
export type OllamaSettings = {
  settings: Record<string, string | number | boolean | null>;
  settingFields: OllamaSettingField[];
  baseUrl: string;
  modelName: string;
  timeoutSeconds: number;
  temperature: number;
  numPredict: number;
  numCtx: number;
  systemPrompt: string;
  defaultSystemPrompt: string;
  toolSystemPrompt: string;
  defaultToolSystemPrompt: string;
};

export type OllamaSettingField = {
  key: string;
  label: string;
  inputType: "text" | "number" | "boolean";
  hint: string;
  min: number | null;
  max: number | null;
  step: number | null;
  modelField: boolean;
};

export type ConfigurationProvider = "OLLAMA";

/** Response body for listing models available from an Ollama endpoint. */
export type OllamaModelsResponse = {
  models: string[];
};

/** A message in the local chat log (not persisted on the server). */
export type ChatMessage = {
  role: "user" | "assistant" | "system";
  content: string;
  /** Optional stable identity used to update a streaming message in-place. */
  id?: string;
};

/** Shared state for a failed API signal, synchronized between chat and canvas. */
export type ApiFailureState = {
  id: string;
  signal: string;
  endpoint: string;
  message: string;
  statusCode?: number;
  contentType?: string | null;
  timestamp: string;
  iframeWindowIds: number[];
};

/** Runtime state for an open project explorer window. */
export type WindowState = {
  id: number;
  project: ProjectSummary;
  entriesLoading: boolean;
  entriesError?: string;
  entries?: FsBrowserEntry[];
  selectedFilePath?: string;
  selectedFileUuid?: string;
  fileContent?: string;
  fileContentMarkdown?: boolean;
  fileLoading?: boolean;
  fileLoadingFolderSummary?: boolean;
  folderSummaryStatus?: "idle" | "generating" | "generated" | "error";
  folderViewMode?: "preview" | "subsummaries";
  fileError?: string;
  x: number;
  y: number;
  z: number;
};

/** Runtime state for a standalone file preview window. */
export type FileWindowState = {
  id: number;
  filePath: string;
  title: string;
  loading: boolean;
  content?: string;
  language?: string;
  mimeType?: string;
  error?: string;
  x: number;
  y: number;
  z: number;
};

/** Runtime state for a fullscreen HTML response overlay inside the canvas area. */
export type IframeWindowState = {
  id: number;
  failureId?: string;
  title: string;
  html: string;
  signal: string;
  endpoint: string;
  statusCode?: number;
  z: number;
};
