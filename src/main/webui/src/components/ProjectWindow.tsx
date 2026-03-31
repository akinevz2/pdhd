import React, { useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { WindowState } from "../types";
import { isImagePath, isPdfPath, rawFileUrl, rawImageUrl } from "../utils";
import { TreeView } from "./TreeView";

const MARKDOWN_SIGNAL_PATTERN =
  /(^|\n)\s{0,3}#{1,6}\s|(^|\n)\s*[-*+]\s|(^|\n)\s*\d+\.\s|```|\[[^\]]+\]\([^\)]+\)/m;

export type ProjectWindowProps = {
  windowState: WindowState;
  highlighted: Set<string>;
  onClose: () => void;
  onFocus: () => void;
  onMove: (x: number, y: number) => void;
  onOpenFile: (path: string) => void;
  onOpenFolderSummary: (path: string) => void;
};

/**
 * A floating, draggable explorer window for a single project.
 *
 * Renders a file-tree pane on the left and a content preview pane on the
 * right.  Drag state is tracked in a ref (not state) to avoid re-renders on
 * every mouse-move event.
 */
export function ProjectWindow({
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
  }>({ active: false, startX: 0, startY: 0, originX: 0, originY: 0 });

  useEffect(() => {
    const onMouseMove = (e: MouseEvent) => {
      if (!dragRef.current.active) return;
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

  return (
    <section
      className="window-card window"
      style={{
        left: windowState.x,
        top: windowState.y,
        zIndex: windowState.z,
      }}
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
                onOpenFile={onOpenFile}
                onOpenFolderSummary={onOpenFolderSummary}
              />
            )}
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
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {windowState.fileContent || ""}
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
    </section>
  );
}
