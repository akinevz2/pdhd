/** Summary of a discovered software project returned by the API. */
export type ProjectSummary = {
  id: number;
  directory: string;
  hasGitRepository: boolean;
};

/** A node in a project's file-tree response. */
export type TreeNode = {
  name: string;
  relativePath: string;
  directory: boolean;
  children: TreeNode[];
};

/** A single tool-call trace emitted by the AI assistant. */
export type ToolActivityItem = {
  timestamp: string;
  toolName: string;
  argumentsJson: string;
  result: string;
  requestedFiles: string[];
};

/** Wrapper returned by the tool-activity API endpoint. */
export type ToolActivityResponse = {
  items: ToolActivityItem[];
};

/** Response from file-content API endpoint. */
export type FileContentResponse = {
  filePath: string;
  content: string;
};

/** Response from the CWD API endpoints. */
export type CwdResponse = {
  cwd: string;
};

/** A single filesystem row in the simple left-pane browser. */
export type FsBrowserEntry = {
  name: string;
  path: string;
  directory: boolean;
  repoUrl?: string | null;
};

/** Response from the lightweight filesystem browser endpoint. */
export type FsListResponse = {
  path: string;
  entries: FsBrowserEntry[];
  repoUrl?: string | null;
};

/** Response from the assistant chat API. */
export type AssistantChatResponse = {
  reply: string;
};

/** Persisted Ollama configuration used by the frontend menus. */
export type OllamaSettings = {
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

/** Response body for listing models available from an Ollama endpoint. */
export type OllamaModelsResponse = {
  models: string[];
};

/** A message in the local chat log (not persisted on the server). */
export type ChatMessage = {
  role: "user" | "assistant" | "system";
  content: string;
};

/** Runtime state for an open project explorer window. */
export type WindowState = {
  id: number;
  project: ProjectSummary;
  treeLoading: boolean;
  treeError?: string;
  tree?: TreeNode;
  selectedFilePath?: string;
  fileContent?: string;
  fileContentMarkdown?: boolean;
  fileLoading?: boolean;
  fileLoadingFolderSummary?: boolean;
  fileError?: string;
  x: number;
  y: number;
  z: number;
};
