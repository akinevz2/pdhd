import type { Dispatch, SetStateAction } from "react";
import type { OllamaSettings, ToolActivityItem } from "../types";

type TopMenuAndModalsProps = {
  ollamaLoading: boolean;
  onOpenOllamaConfig: () => Promise<void>;
  onOpenSystemPrompt: () => Promise<void>;
  onOpenDebug: () => void;
  onExit: () => Promise<void>;

  ollamaOpen: boolean;
  ollamaForm: OllamaSettings | null;
  ollamaError: string | null;
  availableModels: string[];
  modelsLoading: boolean;
  setOllamaOpen: Dispatch<SetStateAction<boolean>>;
  setOllamaForm: Dispatch<SetStateAction<OllamaSettings | null>>;
  fetchModels: (baseUrl: string) => Promise<void>;
  saveOllamaConfig: () => Promise<void>;
  ollamaSaving: boolean;

  promptOpen: boolean;
  promptDraft: string;
  promptError: string | null;
  promptDefault: string;
  toolPromptDraft: string;
  toolPromptDefault: string;
  setPromptOpen: Dispatch<SetStateAction<boolean>>;
  setPromptDraft: Dispatch<SetStateAction<string>>;
  setToolPromptDraft: Dispatch<SetStateAction<string>>;
  saveSystemPrompt: () => Promise<void>;
  promptSaving: boolean;

  debugOpen: boolean;
  setDebugOpen: Dispatch<SetStateAction<boolean>>;
  cwd: string;
  activityItems: ToolActivityItem[];
};

export function TopMenuAndModals({
  ollamaLoading,
  onOpenOllamaConfig,
  onOpenSystemPrompt,
  onOpenDebug,
  onExit,
  ollamaOpen,
  ollamaForm,
  ollamaError,
  availableModels,
  modelsLoading,
  setOllamaOpen,
  setOllamaForm,
  fetchModels,
  saveOllamaConfig,
  ollamaSaving,
  promptOpen,
  promptDraft,
  promptError,
  promptDefault,
  toolPromptDraft,
  toolPromptDefault,
  setPromptOpen,
  setPromptDraft,
  setToolPromptDraft,
  saveSystemPrompt,
  promptSaving,
  debugOpen,
  setDebugOpen,
  cwd,
  activityItems,
}: TopMenuAndModalsProps) {
  return (
    <>
      <nav className="menu-bar panel">
        <span className="menu-brand">PDHD</span>
        <button
          className="menu-btn"
          onClick={() => {
            onOpenOllamaConfig().catch(() => {});
          }}
          disabled={ollamaLoading}
        >
          {ollamaLoading ? "Loading..." : "Ollama"}
        </button>
        <button
          className="menu-btn"
          onClick={() => {
            onOpenSystemPrompt().catch(() => {});
          }}
        >
          System Prompt
        </button>
        <button className="menu-btn" onClick={onOpenDebug}>
          Debug
        </button>
        <button
          className="menu-btn menu-btn-exit"
          onClick={() => {
            onExit().catch(() => {});
          }}
        >
          Exit
        </button>
      </nav>

      {ollamaOpen && ollamaForm && (
        <div
          className="modal-overlay"
          onClick={(e) => {
            if (e.target === e.currentTarget) setOllamaOpen(false);
          }}
        >
          <div className="modal-panel">
            <div className="modal-header">
              <span className="modal-title">Configure Ollama</span>
              <button
                className="modal-close"
                onClick={() => setOllamaOpen(false)}
              >
                X
              </button>
            </div>
            <div className="modal-body">
              {ollamaError && <p className="form-error">{ollamaError}</p>}
              <div className="form-grid">
                <label>Base URL</label>
                <input
                  className="form-input"
                  value={ollamaForm.baseUrl}
                  onChange={(e) =>
                    setOllamaForm({ ...ollamaForm, baseUrl: e.target.value })
                  }
                />
                <label>Model</label>
                <div className="model-row">
                  {availableModels.length > 0 ? (
                    <select
                      className="form-input"
                      value={ollamaForm.modelName}
                      onChange={(e) =>
                        setOllamaForm({
                          ...ollamaForm,
                          modelName: e.target.value,
                        })
                      }
                    >
                      {!availableModels.includes(ollamaForm.modelName) && (
                        <option value={ollamaForm.modelName}>
                          {ollamaForm.modelName}
                        </option>
                      )}
                      {availableModels.map((m) => (
                        <option key={m} value={m}>
                          {m}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input
                      className="form-input"
                      value={ollamaForm.modelName}
                      placeholder="e.g. llama3.2"
                      onChange={(e) =>
                        setOllamaForm({
                          ...ollamaForm,
                          modelName: e.target.value,
                        })
                      }
                    />
                  )}
                  <button
                    onClick={() => {
                      fetchModels(ollamaForm.baseUrl).catch(() => {});
                    }}
                    disabled={modelsLoading}
                  >
                    {modelsLoading ? "..." : "Fetch"}
                  </button>
                </div>
                <label>Timeout (s)</label>
                <input
                  className="form-input"
                  type="number"
                  min={1}
                  value={ollamaForm.timeoutSeconds}
                  onChange={(e) =>
                    setOllamaForm({
                      ...ollamaForm,
                      timeoutSeconds: Number(e.target.value),
                    })
                  }
                />
                <label>Temperature</label>
                <div className="slider-row">
                  <input
                    className="form-range"
                    type="range"
                    min={0}
                    max={2}
                    step={0.05}
                    value={ollamaForm.temperature}
                    onChange={(e) =>
                      setOllamaForm({
                        ...ollamaForm,
                        temperature: parseFloat(e.target.value),
                      })
                    }
                  />
                  <span>{ollamaForm.temperature.toFixed(2)}</span>
                </div>
                <label>Num Predict</label>
                <div className="hint-row">
                  <input
                    className="form-input"
                    type="number"
                    value={ollamaForm.numPredict}
                    onChange={(e) =>
                      setOllamaForm({
                        ...ollamaForm,
                        numPredict: Number(e.target.value),
                      })
                    }
                  />
                  <span className="form-hint">-1 = model default</span>
                </div>
                <label>Context Window</label>
                <div className="hint-row">
                  <input
                    className="form-input"
                    type="number"
                    min={0}
                    value={ollamaForm.numCtx}
                    onChange={(e) =>
                      setOllamaForm({
                        ...ollamaForm,
                        numCtx: Number(e.target.value),
                      })
                    }
                  />
                  <span className="form-hint">0 = model default</span>
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button onClick={() => setOllamaOpen(false)}>Cancel</button>
              <button
                onClick={() => {
                  saveOllamaConfig().catch(() => {});
                }}
                disabled={ollamaSaving}
              >
                {ollamaSaving ? "Saving..." : "Save"}
              </button>
            </div>
          </div>
        </div>
      )}

      {promptOpen && (
        <div
          className="modal-overlay"
          onClick={(e) => {
            if (e.target === e.currentTarget) setPromptOpen(false);
          }}
        >
          <div className="modal-panel modal-panel-wide">
            <div className="modal-header">
              <span className="modal-title">System Prompt</span>
              <button
                className="modal-close"
                onClick={() => setPromptOpen(false)}
              >
                X
              </button>
            </div>
            <div className="modal-body">
              {promptError && <p className="form-error">{promptError}</p>}
              <label style={{ display: "block", marginBottom: 8 }}>
                Main assistant prompt
              </label>
              <textarea
                className="prompt-textarea"
                value={promptDraft}
                onChange={(e) => setPromptDraft(e.target.value)}
              />
              <label style={{ display: "block", margin: "12px 0 8px" }}>
                Tool agent prompt
              </label>
              <textarea
                className="prompt-textarea"
                value={toolPromptDraft}
                onChange={(e) => setToolPromptDraft(e.target.value)}
              />
            </div>
            <div className="modal-footer">
              <button
                onClick={() => {
                  setPromptDraft(promptDefault);
                  setToolPromptDraft(toolPromptDefault);
                }}
              >
                Reset Both to Default
              </button>
              <button onClick={() => setPromptOpen(false)}>Cancel</button>
              <button
                onClick={() => {
                  saveSystemPrompt().catch(() => {});
                }}
                disabled={promptSaving}
              >
                {promptSaving ? "Saving..." : "Save"}
              </button>
            </div>
          </div>
        </div>
      )}

      {debugOpen && (
        <div
          className="modal-overlay"
          onClick={(e) => {
            if (e.target === e.currentTarget) setDebugOpen(false);
          }}
        >
          <div className="modal-panel modal-panel-wide">
            <div className="modal-header">
              <span className="modal-title">Debug</span>
              <button
                className="modal-close"
                onClick={() => setDebugOpen(false)}
              >
                X
              </button>
            </div>
            <div className="modal-body">
              <div className="debug-section">
                <strong>Working Directory</strong>
                <code>{cwd || "-"}</code>
              </div>
              <div className="debug-section">
                <strong>Recent Tool Traces</strong>
                {activityItems.length === 0 && (
                  <p style={{ opacity: 0.6, fontSize: 12 }}>No traces yet.</p>
                )}
                {activityItems
                  .slice()
                  .reverse()
                  .slice(0, 30)
                  .map((event, index) => (
                    <div
                      key={`${event.timestamp}-${event.toolName}-${index}`}
                      className="debug-trace"
                    >
                      <div className="debug-trace-head">
                        <strong>{event.toolName}</strong>
                        <small>
                          {new Date(event.timestamp).toLocaleTimeString()}
                        </small>
                      </div>
                      {event.requestedFiles &&
                        event.requestedFiles.length > 0 && (
                          <div className="debug-trace-files">
                            {event.requestedFiles.join(", ")}
                          </div>
                        )}
                      {event.argumentsJson && (
                        <pre className="debug-trace-json">
                          {event.argumentsJson}
                        </pre>
                      )}
                    </div>
                  ))}
              </div>
            </div>
            <div className="modal-footer">
              <button onClick={() => setDebugOpen(false)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
