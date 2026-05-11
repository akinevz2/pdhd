import React from "react";
import {
  RetryableErrorCard,
  ErrorSummaryCard,
} from "../modules/error-handling";
import type { ApiFailureState, ChatMessage, IframeWindowState } from "../types";

export type ChatDockProps = {
  chatUnavailable: boolean;
  apiFailures: ApiFailureState[];
  iframeWindows: IframeWindowState[];
  chatMessages: ChatMessage[];
  chatLoading: boolean;
  chatInput: string;
  chatLogRef: React.RefObject<HTMLDivElement>;
  chatInputRef: React.RefObject<HTMLTextAreaElement>;
  setChatUnavailable: (value: boolean) => void;
  setChatInput: (value: string) => void;
  dismissApiError: (id: string) => void;
  sendChatMessage: (overrideMessage?: string, source?: string) => Promise<void>;
  resetChat: () => Promise<void>;
  renderAssistantMarkdown: (content: string) => React.ReactNode;
};

export function ChatDock({
  chatUnavailable,
  apiFailures,
  iframeWindows,
  chatMessages,
  chatLoading,
  chatInput,
  chatLogRef,
  chatInputRef,
  setChatUnavailable,
  setChatInput,
  dismissApiError,
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

  const handleDismiss = () => {
    setChatUnavailable(false);
  };

  return (
    <section className="chat-dock panel">
      <div className="toolbar">
        <div className="toolbar-title-row">
          <strong>Assistant Chat</strong>
          {chatLoading && (
            <span className="chat-stream-status">Streaming...</span>
          )}
        </div>
        <button onClick={resetChat} disabled={chatLoading}>
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

          {apiFailures.map((apiFailure) => {
            const htmlWindow = iframeWindows.find(
              (w) => w.failureId === apiFailure.id,
            );
            return (
              <ErrorSummaryCard
                key={apiFailure.id}
                failure={apiFailure}
                htmlErrorContent={htmlWindow?.html}
                onDismiss={dismissApiError}
              />
            );
          })}

          {hasNonApiErrorState && (
            <RetryableErrorCard
              messages={nonApiErrorMessages}
              onDismiss={handleDismiss}
              disabled={chatLoading}
            />
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
