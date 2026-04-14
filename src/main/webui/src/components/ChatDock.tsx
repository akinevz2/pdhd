import React from "react";
import type { ApiFailureState, ChatMessage } from "../types";

export type ChatDockProps = {
  chatUnavailable: boolean;
  apiFailures: ApiFailureState[];
  chatMessages: ChatMessage[];
  chatLoading: boolean;
  chatInput: string;
  retryMessage: string | null;
  chatLogRef: React.RefObject<HTMLDivElement>;
  chatInputRef: React.RefObject<HTMLTextAreaElement>;
  setChatUnavailable: (value: boolean) => void;
  setChatInput: (value: string) => void;
  dismissApiError: (id: string) => void;
  retryApiError: (id: string) => Promise<void>;
  sendChatMessage: (overrideMessage?: string, source?: string) => Promise<void>;
  resetChat: () => Promise<void>;
  renderAssistantMarkdown: (content: string) => React.ReactNode;
};

export function ChatDock({
  chatUnavailable,
  apiFailures,
  chatMessages,
  chatLoading,
  chatInput,
  retryMessage,
  chatLogRef,
  chatInputRef,
  setChatUnavailable,
  setChatInput,
  dismissApiError,
  retryApiError,
  sendChatMessage,
  resetChat,
  renderAssistantMarkdown,
}: ChatDockProps) {
  const unreachableMessage = chatUnavailable
    ? "Assistant is unreachable - check that Ollama is running and the model is loaded."
    : null;
  const nonApiErrorMessages = [unreachableMessage].filter(
    (value): value is string => Boolean(value),
  );
  const hasNonApiErrorState = nonApiErrorMessages.length > 0;

  const handleRetry = () => {
    if (retryMessage) {
      sendChatMessage(retryMessage).catch(() => {
        // handled in callback
      });
      return;
    }

    setChatUnavailable(false);
  };

  const handleDismiss = () => {
    setChatUnavailable(false);
  };

  return (
    <section className="chat-dock panel">
      <div className="toolbar">
        <strong>Assistant Chat</strong>
        <button onClick={() => resetChat()} disabled={chatLoading}>
          Reset
        </button>
      </div>

      <div className="chat-log-wrap">
        <div className="chat-log" ref={chatLogRef}>
          {chatMessages.length === 0 && (
            <p className="chat-empty">Ask the assistant about this project.</p>
          )}
          {chatMessages.map((entry, idx) => (
            <div
              key={`${entry.role}-${idx}`}
              className={`chat-row ${entry.role}`}
            >
              <strong>
                {entry.role === "user"
                  ? "You"
                  : entry.role === "assistant"
                    ? "Assistant"
                    : "System"}
              </strong>
              {entry.role === "assistant" ? (
                <div className="chat-message-markdown">
                  {entry.content
                    ? renderAssistantMarkdown(entry.content)
                    : chatLoading
                      ? "▊"
                      : ""}
                </div>
              ) : (
                <span>{entry.content}</span>
              )}
            </div>
          ))}
          {chatLoading && (
            <div className="chat-row assistant">Assistant is thinking...</div>
          )}

          {apiFailures.map((apiFailure) => (
            <div
              key={apiFailure.id}
              className="chat-errors"
              role="alert"
              aria-live="polite"
            >
              <p className="chat-error">
                {apiFailure.message} [signal {apiFailure.signal}]
              </p>
              <div className="chat-retry-row">
                <button
                  className="retry-button"
                  onClick={() => dismissApiError(apiFailure.id)}
                  disabled={chatLoading}
                >
                  Dismiss
                </button>
                <button
                  className="retry-button"
                  onClick={() => {
                    retryApiError(apiFailure.id).catch(() => {
                      // handled in callback
                    });
                  }}
                  disabled={chatLoading}
                >
                  Retry
                </button>
              </div>
            </div>
          ))}

          {hasNonApiErrorState && (
            <div className="chat-errors" role="alert" aria-live="polite">
              {nonApiErrorMessages.map((message, idx) => (
                <p key={`${message}-${idx}`} className="chat-error">
                  {message}
                </p>
              ))}
              <div className="chat-retry-row">
                <button
                  className="retry-button"
                  onClick={handleDismiss}
                  disabled={chatLoading}
                >
                  Dismiss
                </button>
                <button
                  className="retry-button"
                  onClick={handleRetry}
                  disabled={chatLoading}
                >
                  Retry
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="chat-compose">
        <textarea
          ref={chatInputRef}
          value={chatInput}
          onChange={(e) => setChatInput(e.target.value)}
          placeholder="Type a message..."
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              sendChatMessage().catch(() => {
                // handled in callback
              });
            }
          }}
          disabled={chatLoading}
        />
        <button
          onClick={() => {
            sendChatMessage().catch(() => {
              // handled in callback
            });
          }}
          disabled={chatLoading || !chatInput.trim()}
        >
          Send
        </button>
      </div>
    </section>
  );
}
