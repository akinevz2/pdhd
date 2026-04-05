import React from "react";
import type { ChatMessage } from "../types";

export type ChatDockProps = {
  assistantUnreachable: boolean;
  chatMessages: ChatMessage[];
  chatLoading: boolean;
  chatInput: string;
  chatError: string | null;
  retryMessage: string | null;
  chatLogRef: React.RefObject<HTMLDivElement>;
  chatInputRef: React.RefObject<HTMLTextAreaElement>;
  setAssistantUnreachable: (value: boolean) => void;
  setChatInput: (value: string) => void;
  sendChatMessage: (overrideMessage?: string, source?: string) => Promise<void>;
  resetChat: () => Promise<void>;
  renderAssistantMarkdown: (content: string) => React.ReactNode;
};

export function ChatDock({
  assistantUnreachable,
  chatMessages,
  chatLoading,
  chatInput,
  chatError,
  retryMessage,
  chatLogRef,
  chatInputRef,
  setAssistantUnreachable,
  setChatInput,
  sendChatMessage,
  resetChat,
  renderAssistantMarkdown,
}: ChatDockProps) {
  return (
    <section className="chat-dock panel">
      <div className="toolbar">
        <strong>Assistant Chat</strong>
        <button onClick={() => resetChat()} disabled={chatLoading}>
          Reset
        </button>
      </div>

      {assistantUnreachable && (
        <div className="assistant-unreachable-notice">
          <span>
            Assistant is unreachable - check that Ollama is running and the
            model is loaded.
          </span>
          <button
            className="notice-dismiss"
            onClick={() => setAssistantUnreachable(false)}
            aria-label="Dismiss"
          >
            X
          </button>
        </div>
      )}

      <div className="chat-log" ref={chatLogRef}>
        {chatMessages.length === 0 && (
          <p className="chat-empty">Ask the assistant about this project.</p>
        )}
        {chatMessages.map((entry, idx) => (
          <div key={`${entry.role}-${idx}`} className={`chat-row ${entry.role}`}>
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
        {retryMessage && (
          <button
            className="retry-button"
            onClick={() => sendChatMessage(retryMessage)}
            disabled={chatLoading}
            style={{ marginLeft: 8 }}
          >
            Retry
          </button>
        )}
      </div>
      {chatError && <p className="chat-error">{chatError}</p>}
    </section>
  );
}
