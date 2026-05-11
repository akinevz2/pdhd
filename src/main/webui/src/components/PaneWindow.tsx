import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";
import { normalizeToolCallMarkup } from "../assistantActions";
import type { WindowState } from "../types";
import { isImagePath, isPdfPath, rawFileUrl, rawImageUrl } from "../utils";
import { Window } from "./Window";

type AssistantActionPayload = {
  label: string;
  prompt: string;
};

export type PaneWindowProps = {
  windowState: WindowState;
  onClose: () => void;
  onFocus: () => void;
  onMove: (x: number, y: number) => void;
  onOpenFile: (path: string, uuid: string) => void;
  onOpenFolderPreview: (path: string, uuid?: string | null) => void;
  onGenerateFolderSummary: (path: string, uuid?: string | null) => void;
  onShowFolderSubsummaries: (path: string, uuid?: string | null) => void;
  onAssistantAction: (prompt: string) => void;
  assistantActionDisabled?: boolean;
};

/** A pane-style window with tree on left and preview on right. */
export function PaneWindow({
  windowState,
  onClose,
  onFocus,
  onMove,
  onOpenFile,
  onOpenFolderPreview,
  onGenerateFolderSummary,
  onShowFolderSubsummaries,
  onAssistantAction,
  assistantActionDisabled = false,
}: PaneWindowProps) {
  const toUnixPath = (value: string): string => value.replace(/\\/g, "/");

  const isExternalAsset = (value: string): boolean =>
    /^(?:https?:|data:|blob:|mailto:|#)/i.test(value);

  const stripFolderSummaryScaffolding = (content: string): string => {
    if (!content) {
      return content;
    }

    return content
      .replace(/^===.*?===\s*$/gm, "")
      .replace(/\(evidence only\)/gi, "")
      .replace(/\.\.\.\(truncated\)/gi, "")
      .replace(/\n{3,}/g, "\n\n")
      .trim();
  };

  const normalizeAbsolutePath = (value: string): string => {
    const unix = toUnixPath(value);
    const isAbsolute = unix.startsWith("/") || /^[A-Za-z]:\//.test(unix);
    const hasDrive = /^[A-Za-z]:\//.test(unix);
    const drivePrefix = hasDrive ? unix.slice(0, 2) : "";
    const body = hasDrive ? unix.slice(2) : unix;

    const segments = body.split("/");
    const normalized: string[] = [];
    for (const segment of segments) {
      if (!segment || segment === ".") {
        continue;
      }
      if (segment === "..") {
        if (normalized.length > 0) {
          normalized.pop();
        }
        continue;
      }
      normalized.push(segment);
    }

    const prefix = hasDrive ? `${drivePrefix}/` : isAbsolute ? "/" : "";
    return `${prefix}${normalized.join("/")}`;
  };

  const resolveMarkdownAssetPath = (assetPath: string): string | null => {
    if (!assetPath || isExternalAsset(assetPath)) {
      return null;
    }

    const selectedPath = windowState.selectedFilePath || "";
    if (!selectedPath || selectedPath === ".") {
      return null;
    }

    const selectedUnix = toUnixPath(selectedPath);
    const baseDir = selectedUnix.includes("/")
      ? selectedUnix.slice(0, selectedUnix.lastIndexOf("/")) || "/"
      : selectedUnix;

    const target = assetPath.startsWith("/")
      ? assetPath
      : `${baseDir}/${assetPath}`;

    return normalizeAbsolutePath(target);
  };

  const markdownImageSrc = (assetPath?: string): string | undefined => {
    const resolved = resolveMarkdownAssetPath(assetPath || "");
    if (!resolved) {
      return assetPath;
    }
    return `/api/project/${encodeURIComponent(windowState.project.id)}/raw?path=${encodeURIComponent(resolved)}`;
  };

  const showImage =
    !windowState.fileLoading &&
    !windowState.fileError &&
    windowState.selectedFilePath &&
    isImagePath(windowState.selectedFilePath);

  const showPdf =
    !windowState.fileLoading &&
    !windowState.fileError &&
    !!windowState.selectedFilePath &&
    isPdfPath(windowState.selectedFilePath);

  const isFolderSummaryPath =
    windowState.selectedFilePath === "." ||
    (!showImage &&
      !showPdf &&
      !!windowState.selectedFilePath &&
      !windowState.selectedFilePath.includes("."));

  const showMarkdown =
    !windowState.fileLoading &&
    !windowState.fileError &&
    !showImage &&
    !showPdf &&
    !!windowState.fileContent &&
    (!!windowState.fileContentMarkdown || isFolderSummaryPath);

  const normalizedMarkdownContent = isFolderSummaryPath
    ? stripFolderSummaryScaffolding(
        normalizeToolCallMarkup(windowState.fileContent || ""),
      )
    : normalizeToolCallMarkup(windowState.fileContent || "");

  const hasContentSelection =
    !!windowState.selectedFilePath ||
    !!windowState.fileLoading ||
    !!windowState.fileError;

  const showSummaryControls =
    isFolderSummaryPath &&
    !!windowState.selectedFilePath &&
    !!windowState.selectedFileUuid;

  const summaryIndicatorLabel =
    windowState.folderSummaryStatus === "generated"
      ? "Summary stored"
      : windowState.folderSummaryStatus === "generating"
        ? "Generating summary"
        : windowState.folderSummaryStatus === "error"
          ? "Summary generation failed"
          : "No generated summary";

  const isShowingSubsummaries = windowState.folderViewMode === "subsummaries";

  const parseAssistantActionPayload = (
    rawPayload: string,
  ): AssistantActionPayload | null => {
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
  };

  const markdownComponents: Components = {
    code({ className, children, ...props }) {
      const language = className?.replace(/^language-/, "") || "";
      if (language !== "assistant-action") {
        return (
          <code className={className} {...props}>
            {children}
          </code>
        );
      }

      const payload = parseAssistantActionPayload(String(children || ""));
      if (!payload) {
        return (
          <code className={className} {...props}>
            {children}
          </code>
        );
      }

      return (
        <button
          className="assistant-action-button"
          disabled={assistantActionDisabled}
          onClick={() => onAssistantAction(payload.prompt)}
          title={payload.prompt}
        >
          {payload.label}
        </button>
      );
    },
    img({ src, alt, title, ...props }) {
      const rewrittenSrc = markdownImageSrc(
        typeof src === "string" ? src : undefined,
      );
      return (
        <img src={rewrittenSrc} alt={alt || ""} title={title} {...props} />
      );
    },
  };

  const repositoryLabel = windowState.project.hasGithubRepository
    ? "GitHub"
    : windowState.project.hasGitRepository
      ? "Git"
      : "";

  const windowTitle = repositoryLabel
    ? `${windowState.project.directory} [${repositoryLabel}]`
    : windowState.project.directory;

  return (
    <Window
      title={windowTitle}
      x={windowState.x}
      y={windowState.y}
      z={windowState.z}
      onClose={onClose}
      onFocus={onFocus}
      onMove={onMove}
    >
      <div className="win-body with-tree">
        <aside className="tree-pane">
          {windowState.entriesLoading && <div>Loading...</div>}
          {!windowState.entriesLoading && windowState.entriesError && (
            <div>{windowState.entriesError}</div>
          )}
          {!windowState.entriesLoading &&
            !windowState.entriesError &&
            windowState.entries?.map((entry) => (
              <div
                key={entry.uuid ?? entry.path}
                className="file-node"
                onClick={
                  entry.directory
                    ? () => onOpenFolderPreview(entry.path, entry.uuid)
                    : () => onOpenFile(entry.path, entry.uuid)
                }
              >
                <span className="node-entry">
                  <span
                    className={`node-icon ${entry.directory ? "folder" : "file"}`}
                    aria-hidden="true"
                  >
                    {entry.directory ? "▸" : "●"}
                  </span>
                  <span className="node-label">{entry.name}</span>
                </span>
              </div>
            ))}
        </aside>

        <article className="content-pane">
          {!hasContentSelection && (
            <pre>Select a file or folder to preview.</pre>
          )}
          {windowState.fileLoading && (
            <pre>
              {windowState.fileLoadingFolderSummary
                ? "Analyzing folder structure..."
                : "Loading file..."}
            </pre>
          )}
          {!windowState.fileLoading && windowState.fileError && (
            <pre>{windowState.fileError}</pre>
          )}
          {showImage && (
            <div className="image-preview-wrap">
              <img
                className="image-preview"
                src={rawImageUrl(
                  windowState.project.id,
                  windowState.selectedFileUuid!,
                )}
                alt={windowState.selectedFilePath}
              />
              <div className="image-caption">
                {windowState.selectedFilePath}
              </div>
            </div>
          )}
          {showPdf && (
            <div className="pdf-preview-wrap">
              <iframe
                className="pdf-preview"
                src={rawFileUrl(
                  windowState.project.id,
                  windowState.selectedFileUuid!,
                )}
                title={windowState.selectedFilePath}
              />
              <div className="image-caption">
                {windowState.selectedFilePath}
              </div>
            </div>
          )}
          {showMarkdown && (
            <div className="file-markdown">
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={markdownComponents}
              >
                {normalizedMarkdownContent}
              </ReactMarkdown>
            </div>
          )}
          {!windowState.fileLoading &&
            !windowState.fileError &&
            !showImage &&
            !showPdf &&
            !showMarkdown &&
            hasContentSelection && <pre>{windowState.fileContent || ""}</pre>}

          {showSummaryControls && (
            <div className="folder-summary-actions">
              <span
                className={`summary-indicator summary-indicator-${windowState.folderSummaryStatus || "idle"}`}
                aria-label={summaryIndicatorLabel}
                title={summaryIndicatorLabel}
              />
              <button
                className="folder-summary-button"
                disabled={
                  assistantActionDisabled ||
                  windowState.fileLoading ||
                  windowState.folderSummaryStatus === "generating"
                }
                onClick={() =>
                  onGenerateFolderSummary(
                    windowState.selectedFilePath || ".",
                    windowState.selectedFileUuid,
                  )
                }
              >
                Generate Summary
              </button>
              <button
                className="folder-summary-button"
                disabled={assistantActionDisabled || windowState.fileLoading}
                onClick={() => {
                  if (isShowingSubsummaries) {
                    onOpenFolderPreview(
                      windowState.selectedFilePath || ".",
                      windowState.selectedFileUuid,
                    );
                    return;
                  }

                  onShowFolderSubsummaries(
                    windowState.selectedFilePath || ".",
                    windowState.selectedFileUuid,
                  );
                }}
              >
                {isShowingSubsummaries ? "Show Preview" : "Show Subsummaries"}
              </button>
            </div>
          )}
        </article>
      </div>
    </Window>
  );
}
