import React, { useState } from "react";
import type { ApiFailureState } from "../../types";

export type ErrorSummaryCardProps = {
  failure: ApiFailureState;
  htmlErrorContent?: string;
  onDismiss: (id: string) => void;
};

export function ErrorSummaryCard({
  failure,
  htmlErrorContent,
  onDismiss,
}: ErrorSummaryCardProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const timestamp = new Date(failure.timestamp);
  const timeString = timestamp.toLocaleTimeString();

  return (
    <div className="error-summary-card" role="alert" aria-live="polite">
      <div className="error-summary-header">
        <div className="error-summary-title">
          <span className="error-signal-badge">{failure.signal}</span>
          <span className="error-message">{failure.message}</span>
          {failure.statusCode && (
            <span className="error-status-code">[{failure.statusCode}]</span>
          )}
        </div>
        <button
          className="dismiss-button"
          onClick={() => onDismiss(failure.id)}
          aria-label="Dismiss error"
        >
          ✕
        </button>
      </div>

      <button
        className="error-details-toggle"
        onClick={() => setIsExpanded(!isExpanded)}
        aria-expanded={isExpanded}
      >
        {isExpanded ? "Hide" : "View"} Error Details
      </button>

      {isExpanded && (
        <div className="error-details-section">
          <div className="error-detail-group">
            <label>Signal Endpoint:</label>
            <code className="error-endpoint">{failure.endpoint}</code>
          </div>

          {failure.statusCode && (
            <div className="error-detail-group">
              <label>Status Code:</label>
              <span className="error-status">{failure.statusCode}</span>
            </div>
          )}

          {failure.contentType && (
            <div className="error-detail-group">
              <label>Content Type:</label>
              <span className="error-content-type">{failure.contentType}</span>
            </div>
          )}

          <div className="error-detail-group">
            <label>Timestamp:</label>
            <span className="error-timestamp">{timeString}</span>
          </div>

          <div className="error-detail-group">
            <label>Error ID:</label>
            <code className="error-id">{failure.id}</code>
          </div>

          {htmlErrorContent && (
            <div className="error-html-preview">
              <label>Error Response Preview:</label>
              <div className="error-html-frame-container">
                <iframe
                  className="error-html-frame"
                  title={`Error response for ${failure.signal}`}
                  srcDoc={htmlErrorContent}
                  sandbox="allow-same-origin"
                />
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
