import { api, apiPost } from "./api";

export type ApiSignalKey = `${string}:${string}`;

type ApiSignalMethod = "GET" | "POST";

type ApiSignalEndpoint<TPayload> = string | ((payload: TPayload) => string);

export type ApiSignalDefinition<TPayload = unknown> = {
  method: ApiSignalMethod;
  endpoint: ApiSignalEndpoint<TPayload>;
  timeoutMs?: number;
  headers?: Record<string, string>;
};

export type ApiSignalError = {
  id: string;
  signal: ApiSignalKey;
  endpoint: string;
  message: string;
  timestamp: string;
};

type SignalErrorListener = (error: ApiSignalError) => void;

type EmitSignalOptions = {
  timeoutMs?: number;
  headers?: Record<string, string>;
};

type AnySignalDefinition = ApiSignalDefinition<any>;

const signalRegistry = new Map<ApiSignalKey, AnySignalDefinition>();
const signalErrorListeners = new Set<SignalErrorListener>();
const signalErrorHistory: ApiSignalError[] = [];

const MAX_SIGNAL_ERROR_HISTORY = 200;

export function registerApiSignal<TPayload = unknown>(
  key: ApiSignalKey,
  definition: ApiSignalDefinition<TPayload>,
): void {
  signalRegistry.set(key, definition as AnySignalDefinition);
}

export function registerApiSignals(
  entries: ReadonlyArray<[ApiSignalKey, AnySignalDefinition]>,
): void {
  for (const [key, definition] of entries) {
    signalRegistry.set(key, definition);
  }
}

export function getSignalErrors(): ApiSignalError[] {
  return [...signalErrorHistory];
}

export function subscribeSignalErrors(
  listener: SignalErrorListener,
): () => void {
  signalErrorListeners.add(listener);
  return () => {
    signalErrorListeners.delete(listener);
  };
}

export async function emitApiSignal<TPayload, TResult>(
  key: ApiSignalKey,
  payload?: TPayload,
  options?: EmitSignalOptions,
): Promise<TResult> {
  const definition = signalRegistry.get(key);
  if (!definition) {
    const signalError = createSignalError(
      key,
      "[unregistered]",
      `Signal not registered: ${key}`,
    );
    pushSignalError(signalError);
    throw new Error(signalError.message);
  }

  const endpoint = resolveEndpoint(definition.endpoint, payload);
  try {
    if (definition.method === "GET") {
      return await api<TResult>(endpoint);
    }

    const mergedHeaders = {
      ...(definition.headers || {}),
      ...(options?.headers || {}),
    };
    const timeoutMs = options?.timeoutMs ?? definition.timeoutMs;
    return await apiPost<TPayload, TResult>(
      endpoint,
      (payload || {}) as TPayload,
      timeoutMs,
      mergedHeaders,
    );
  } catch (error) {
    const detail = error instanceof Error ? error.message : "Unknown error";
    const signalError = createSignalError(key, endpoint, detail);
    pushSignalError(signalError);
    throw error;
  }
}

function resolveEndpoint<TPayload>(
  endpoint: ApiSignalEndpoint<TPayload>,
  payload?: TPayload,
): string {
  if (typeof endpoint === "function") {
    return endpoint((payload || {}) as TPayload);
  }
  return endpoint;
}

function createSignalError(
  signal: ApiSignalKey,
  endpoint: string,
  message: string,
): ApiSignalError {
  const timestamp = new Date().toISOString();
  return {
    id: `${timestamp}:${signal}:${Math.random().toString(36).slice(2, 8)}`,
    signal,
    endpoint,
    message,
    timestamp,
  };
}

function pushSignalError(error: ApiSignalError): void {
  signalErrorHistory.push(error);
  if (signalErrorHistory.length > MAX_SIGNAL_ERROR_HISTORY) {
    signalErrorHistory.splice(
      0,
      signalErrorHistory.length - MAX_SIGNAL_ERROR_HISTORY,
    );
  }
  for (const listener of signalErrorListeners) {
    listener(error);
  }
}
