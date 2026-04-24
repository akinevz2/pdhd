import type { ReactNode, Dispatch, SetStateAction } from "react";
import type { ToolActivityItem, ToolTelemetryItem } from "../types";

type TopMenuAndModalsProps = {
  menuButtons?: ReactNode;
  statusContent?: ReactNode;
  modalContent?: ReactNode;
  onOpenDebug: () => void;
  onOpenTelemetry: () => void;
  onExit: () => Promise<void>;

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
  menuButtons,
  statusContent,
  modalContent,
  onOpenDebug,
  onOpenTelemetry,
  onExit,
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
  return (
    <>
      <nav className="menu-bar panel">
        <span className="menu-brand">PDHD</span>
        {menuButtons}
        <button className="menu-btn" onClick={onOpenDebug}>
          Debug
        </button>
        <button className="menu-btn" onClick={onOpenTelemetry}>
          Telemetry
        </button>
        <button
          className="menu-btn menu-btn-exit"
          onClick={() => onExit().catch(() => {})}
        >
          Exit
        </button>
      </nav>
      {statusContent}
      {modalContent}

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
                onClick={() => onRefreshTelemetry().catch(() => {})}
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
