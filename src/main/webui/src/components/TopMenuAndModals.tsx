import type { Dispatch, SetStateAction } from "react";
import type {
    OllamaSettingField,
    ToolActivityItem,
    ToolTelemetryItem,
} from "../types";

type TopMenuAndModalsProps = {
  ollamaLoading: boolean;
  onOpenOllamaConfig: () => Promise<void>;
  onOpenSystemPrompt: () => Promise<void>;
  onOpenDebug: () => void;
  onOpenTelemetry: () => void;
  onExit: () => Promise<void>;

  ollamaOpen: boolean;
  ollamaForm: Record<string, string | number | boolean | null> | null;
  ollamaFields: OllamaSettingField[];
  ollamaError: string | null;
  availableModels: string[];
  modelsLoading: boolean;
  setOllamaOpen: Dispatch<SetStateAction<boolean>>;
  setOllamaForm: Dispatch<
    SetStateAction<Record<string, string | number | boolean | null> | null>
  >;
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

  telemetryOpen: boolean;
  setTelemetryOpen: Dispatch<SetStateAction<boolean>>;
  telemetryItems: ToolTelemetryItem[];
  telemetryLoading: boolean;
  telemetryError: string | null;
  telemetrySummary: string;
  telemetryGeneratedAt: string;
  onRefreshTelemetry: () => Promise<void>;
};

export function TopMenuAndModals({
  ollamaLoading,
  onOpenOllamaConfig,
  onOpenSystemPrompt,
  onOpenDebug,
  onOpenTelemetry,
  onExit,
  ollamaOpen,
  ollamaForm,
  ollamaFields,
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
  telemetryOpen,
  setTelemetryOpen,
  telemetryItems,
  telemetryLoading,
  telemetryError,
  telemetrySummary,
  telemetryGeneratedAt,
  onRefreshTelemetry,
}: TopMenuAndModalsProps) {
  const fieldString = (key: string) => String(ollamaForm?.[key] ?? "");
  const fieldNumber = (key: string) => {
    const raw = ollamaForm?.[key];
    if (typeof raw === "number") return raw;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : 0;
  };
  const fieldBoolean = (key: string) => Boolean(ollamaForm?.[key]);

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
        <button className="menu-btn" onClick={onOpenTelemetry}>
          Telemetry
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
                {ollamaFields.map((field) => (
                  <div key={field.key} style={{ display: "contents" }}>
                    <label>{field.label}</label>
                    {field.inputType === "boolean" ? (
                      <div className="hint-row">
                        <input
                          type="checkbox"
                          checked={fieldBoolean(field.key)}
                          onChange={(e) =>
                            setOllamaForm({
                              ...ollamaForm,
                              [field.key]: e.target.checked,
                            })
                          }
                        />
                        {field.hint && (
                          <span className="form-hint">{field.hint}</span>
                        )}
                      </div>
                    ) : field.modelField ? (
                      <div className="model-row">
                        {availableModels.length > 0 ? (
                          <select
                            className="form-input"
                            value={fieldString(field.key)}
                            onChange={(e) =>
                              setOllamaForm({
                                ...ollamaForm,
                                [field.key]: e.target.value,
                              })
                            }
                          >
                            {!availableModels.includes(
                              fieldString(field.key),
                            ) && (
                              <option value={fieldString(field.key)}>
                                {fieldString(field.key)}
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
                            value={fieldString(field.key)}
                            onChange={(e) =>
                              setOllamaForm({
                                ...ollamaForm,
                                [field.key]: e.target.value,
                              })
                            }
                          />
                        )}
                        <button
                          onClick={() => {
                            fetchModels(fieldString("baseUrl")).catch(() => {});
                          }}
                          disabled={modelsLoading}
                        >
                          {modelsLoading ? "..." : "Fetch"}
                        </button>
                      </div>
                    ) : field.inputType === "number" ? (
                      <div className="hint-row">
                        <input
                          className="form-input"
                          type="number"
                          min={field.min ?? undefined}
                          max={field.max ?? undefined}
                          step={field.step ?? 1}
                          value={fieldNumber(field.key)}
                          onChange={(e) =>
                            setOllamaForm({
                              ...ollamaForm,
                              [field.key]: Number(e.target.value),
                            })
                          }
                        />
                        {field.hint && (
                          <span className="form-hint">{field.hint}</span>
                        )}
                      </div>
                    ) : (
                      <div className="hint-row">
                        <input
                          className="form-input"
                          value={fieldString(field.key)}
                          onChange={(e) =>
                            setOllamaForm({
                              ...ollamaForm,
                              [field.key]: e.target.value,
                            })
                          }
                        />
                        {field.hint && (
                          <span className="form-hint">{field.hint}</span>
                        )}
                      </div>
                    )}
                  </div>
                ))}
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

      {telemetryOpen && (
        <div
          className="modal-overlay"
          onClick={(e) => {
            if (e.target === e.currentTarget) setTelemetryOpen(false);
          }}
        >
          <div className="modal-panel modal-panel-telemetry">
            <div className="modal-header">
              <span className="modal-title">Telemetry Sheet</span>
              <button
                className="modal-close"
                onClick={() => setTelemetryOpen(false)}
              >
                X
              </button>
            </div>
            <div className="modal-body telemetry-modal-body">
              <div className="telemetry-meta-row">
                <span>{telemetrySummary || "No summary available"}</span>
                <span>
                  Snapshot:{" "}
                  {telemetryGeneratedAt
                    ? new Date(telemetryGeneratedAt).toLocaleString()
                    : "-"}
                </span>
              </div>

              {telemetryError && <p className="form-error">{telemetryError}</p>}

              <div className="telemetry-sheet-wrap">
                <table
                  className="telemetry-sheet"
                  aria-label="Tool telemetry sheet"
                >
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Tool</th>
                      <th>Module</th>
                      <th>Calls</th>
                      <th>Failures</th>
                      <th>Arg Validation</th>
                      <th>Avg (ms)</th>
                      <th>P50 (ms)</th>
                      <th>P95 (ms)</th>
                      <th>Error Classes</th>
                    </tr>
                  </thead>
                  <tbody>
                    {telemetryItems.length === 0 && !telemetryLoading && (
                      <tr>
                        <td colSpan={10} className="telemetry-empty-cell">
                          No telemetry rows available.
                        </td>
                      </tr>
                    )}
                    {telemetryItems.map((row, index) => (
                      <tr key={`${row.toolName}-${row.moduleName}-${index}`}>
                        <td>{index + 1}</td>
                        <td>{row.toolName}</td>
                        <td>{row.moduleName}</td>
                        <td>{row.invocations}</td>
                        <td>{row.failures}</td>
                        <td>{row.argumentValidationFailures}</td>
                        <td>{row.averageDurationMs.toFixed(2)}</td>
                        <td>{row.p50DurationMs.toFixed(2)}</td>
                        <td>{row.p95DurationMs.toFixed(2)}</td>
                        <td className="telemetry-error-classes">
                          {Object.keys(row.errorClasses || {}).length > 0
                            ? Object.entries(row.errorClasses)
                                .map(([name, count]) => `${name}:${count}`)
                                .join(", ")
                            : "-"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            <div className="modal-footer">
              <button
                onClick={() => {
                  onRefreshTelemetry().catch(() => {});
                }}
                disabled={telemetryLoading}
              >
                {telemetryLoading ? "Refreshing..." : "Refresh"}
              </button>
              <button onClick={() => setTelemetryOpen(false)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
