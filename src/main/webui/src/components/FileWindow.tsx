import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { FileWindowState } from "../types";
import { Window } from "./Window";

type FileWindowProps = {
  windowState: FileWindowState;
  onClose: () => void;
  onFocus: () => void;
  onMove: (x: number, y: number) => void;
};

/** Standalone file-content window. */
export function FileWindow({
  windowState,
  onClose,
  onFocus,
  onMove,
}: FileWindowProps) {
  let markdown = "";
  if (!windowState.loading && !windowState.error) {
    const language = windowState.language || "text";
    const mimeType = windowState.mimeType || "";
    const isMarkdown =
      /^(markdown|md|mdx)$/i.test(language) ||
      mimeType.toLowerCase().includes("markdown");
    markdown = isMarkdown
      ? windowState.content || ""
      : `\`\`\`${language}\n${windowState.content || ""}\n\`\`\``;
  }

  return (
    <Window
      title={windowState.title}
      x={windowState.x}
      y={windowState.y}
      z={windowState.z}
      onClose={onClose}
      onFocus={onFocus}
      onMove={onMove}
    >
      <div className="win-body">
        <article className="content-pane" style={{ width: "100%" }}>
          {windowState.loading && <pre>Loading file...</pre>}
          {!windowState.loading && windowState.error && (
            <pre>{windowState.error}</pre>
          )}
          {!windowState.loading && !windowState.error && (
            <div className="file-markdown">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {markdown}
              </ReactMarkdown>
            </div>
          )}
        </article>
      </div>
    </Window>
  );
}
