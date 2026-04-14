import type { IframeWindowState } from "../types";

type HtmlFrameWindowProps = {
  windowState: IframeWindowState;
  onClose: () => void;
  onFocus: () => void;
};

export function HtmlFrameWindow({
  windowState,
  onClose,
  onFocus,
}: HtmlFrameWindowProps) {
  return (
    <section
      className="html-frame-window window"
      style={{ zIndex: windowState.z }}
      onMouseDown={onFocus}
    >
      <div className="title-bar html-frame-title">
        <div className="title-bar-text" title={windowState.endpoint}>
          {windowState.title}
        </div>
        <div className="title-bar-controls">
          <button aria-label="Close" onClick={onClose} />
        </div>
      </div>
      <iframe
        className="html-frame-iframe"
        title={windowState.title}
        srcDoc={windowState.html}
      />
    </section>
  );
}
