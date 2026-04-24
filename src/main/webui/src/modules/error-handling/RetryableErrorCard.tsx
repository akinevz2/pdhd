type RetryableErrorCardProps = {
  messages: string[];
  onDismiss: () => void;
  onRetry?: () => void;
  disabled?: boolean;
};

export function RetryableErrorCard({
  messages,
  onDismiss,
  onRetry,
  disabled = false,
}: RetryableErrorCardProps) {
  if (messages.length === 0) {
    return null;
  }

  return (
    <div className="chat-errors" role="alert" aria-live="polite">
      {messages.map((message, idx) => (
        <p key={`${message}-${idx}`} className="chat-error">
          {message}
        </p>
      ))}
      <div className="chat-retry-row">
        <button
          className="retry-button"
          onClick={onDismiss}
          disabled={disabled}
        >
          Dismiss
        </button>
        {onRetry && (
          <button
            className="retry-button"
            onClick={onRetry}
            disabled={disabled}
          >
            Retry
          </button>
        )}
      </div>
    </div>
  );
}
