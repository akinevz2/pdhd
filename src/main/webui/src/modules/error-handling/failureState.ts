import type { ApiSignalError } from "../../signals";
import type { ApiFailureState } from "../../types";

export const RETRY_CONFIRMATION_SUFFIX =
  " Retry requested. Click Retry again to confirm, or Dismiss to cancel.";

export const NON_API_RETRY_CONFIRMATION_MESSAGE =
  "Retry requested. Click Retry again to confirm, or Dismiss to cancel.";

export function normalizeApiFailureMessage(
  statusCode: number | undefined,
  message: string,
): string {
  if (typeof statusCode === "number") {
    return `${statusCode}: API unavailable`;
  }
  const statusMatch = /([1-5]\d{2}):\s*API unavailable/.exec(message || "");
  if (statusMatch) {
    return `${statusMatch[1]}: API unavailable`;
  }
  return message;
}

export function toApiFailureState(signalError: ApiSignalError): ApiFailureState {
  return {
    id: signalError.id,
    signal: signalError.signal,
    endpoint: signalError.endpoint,
    message: normalizeApiFailureMessage(signalError.statusCode, signalError.message),
    statusCode: signalError.statusCode,
    contentType: signalError.contentType,
    timestamp: signalError.timestamp,
    iframeWindowIds: [],
  };
}

export function mergeFailuresById(
  existing: ApiFailureState[],
  incoming: ApiFailureState[],
): ApiFailureState[] {
  const merged = new Map(existing.map((item) => [item.id, item]));
  for (const item of incoming) {
    merged.set(item.id, item);
  }
  return Array.from(merged.values()).sort((a, b) =>
    a.timestamp.localeCompare(b.timestamp),
  );
}

export function withRetryConfirmationMessage(
  failure: ApiFailureState,
): ApiFailureState {
  const baseMessage = failure.message.endsWith(RETRY_CONFIRMATION_SUFFIX)
    ? failure.message.slice(0, -RETRY_CONFIRMATION_SUFFIX.length)
    : failure.message;
  return {
    ...failure,
    message: `${baseMessage}${RETRY_CONFIRMATION_SUFFIX}`,
    statusCode: undefined,
  };
}
