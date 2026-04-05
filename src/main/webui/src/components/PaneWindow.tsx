import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { Components } from "react-markdown";
import { normalizeToolCallMarkup } from "../assistantActions";
import type { WindowState } from "../types";
import { isImagePath, isPdfPath, rawFileUrl, rawImageUrl } from "../utils";
import { Window } from "./Window";

const MARKDOWN_SIGNAL_PATTERN =
  /(^|\n)\s{0,3}#{1,6}\s|(^|\n)\s*[-*+]\s|(^|\n)\s*\d+\.\s|```|\[[^\]]+\]\([^\)]+\)/m;

type AssistantActionPayload = {
  label: string;
  prompt: string;
};

export type PaneWindowProps = {
  windowState: WindowState;
  onClose: () => void;
  onFocus: () => void;
  onMove: (x: number, y: number) => void;
  onOpenFile: (path: string) => void;
  onOpenFolderSummary: (path: string) => void;
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
  onOpenFolderSummary,
  onAssistantAction,
  assistantActionDisabled = false,
}: PaneWindowProps) {
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

  const looksLikeMarkdown = !!windowState.fileContent?.match(
    MARKDOWN_SIGNAL_PATTERN,
  );

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
    (!!windowState.fileContentMarkdown ||
      looksLikeMarkdown ||
      isFolderSummaryPath);

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
  };

  const normalizedMarkdownContent = normalizeToolCallMarkup(
    windowState.fileContent || "",
  );

  return (
    <Window
      title={windowState.project.directory}
      x={windowState.x}
      y={windowState.y}
      z={windowState.z}
      onClose={onClose}
      onFocus={onFocus}
      onMove={onMove}
    >
      <div className="win-body">
        <aside className="tree-pane">
          {windowState.entriesLoading && <div>Loading...</div>}
          {!windowState.entriesLoading && windowState.entriesError && (
            <div>{windowState.entriesError}</div>
          )}
          {!windowState.entriesLoading &&
            !windowState.entriesError &&
            windowState.entries?.map((entry) => (
              <div
                key={entry.path}
                className="file-node"
                onClick={
                  entry.directory
                    ? () => onOpenFolderSummary(entry.path)
                    : () => onOpenFile(entry.path)
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
          {windowState.fileLoading && (
            <pre>
              {windowState.fileLoadingFolderSummary
                ? "Loading folder summary..."
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
                  windowState.project.directory,
                  windowState.selectedFilePath!,
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
                  windowState.project.directory,
                  windowState.selectedFilePath!,
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
            !showMarkdown && (
              <pre>
                {windowState.fileContent ||
                  "Select a file or folder to view content."}
              </pre>
            )}
        </article>
      </div>
    </Window>
  );
}
