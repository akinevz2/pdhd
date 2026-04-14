import {
  apiDeleteDetailed,
  apiDetailed,
  apiPostDetailed,
  apiWithTimeoutDetailed,
  HttpResponseError,
} from "./api";

export type ApiSignalKey = `${string}:${string}`;

type ApiSignalMethod = "GET" | "POST"| "DELETE";

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
  statusCode?: number;
  contentType?: string | null;
  timestamp: string;
};

export type ApiSignalHtmlFrame = {
  id: string;
  failureId?: string;
  signal: ApiSignalKey;
  endpoint: string;
  html: string;
  statusCode?: number;
  timestamp: string;
};

type SignalErrorListener = (error: ApiSignalError) => void;
type SignalHtmlFrameListener = (frame: ApiSignalHtmlFrame) => void;

type EmitSignalOptions = {
  timeoutMs?: number;
  headers?: Record<string, string>;
};

type AnySignalDefinition = ApiSignalDefinition<any>;

const signalRegistry = new Map<ApiSignalKey, AnySignalDefinition>();
const signalErrorListeners = new Set<SignalErrorListener>();
const signalHtmlFrameListeners = new Set<SignalHtmlFrameListener>();
let signalErrorHistory: ApiSignalError[] = [];
let signalHtmlFrameHistory: ApiSignalHtmlFrame[] = [];
const failedSignalInvocations = new Map<
  string,
  {
    signal: ApiSignalKey;
    payload: unknown;
    options?: EmitSignalOptions;
  }
>();

const MAX_SIGNAL_ERROR_HISTORY = 200;
const MAX_SIGNAL_HTML_FRAME_HISTORY = 50;

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
  return signalErrorHistory.slice();
}

export function getSignalHtmlFrames(): ApiSignalHtmlFrame[] {
  return signalHtmlFrameHistory.slice();
}

export function subscribeSignalErrors(
  listener: SignalErrorListener,
): () => void {
  signalErrorListeners.add(listener);
  return () => {
    signalErrorListeners.delete(listener);
  };
}

export function subscribeSignalHtmlFrames(
  listener: SignalHtmlFrameListener,
): () => void {
  signalHtmlFrameListeners.add(listener);
  return () => {
    signalHtmlFrameListeners.delete(listener);
  };
}

export async function retryFailedSignal(failureId: string): Promise<unknown> {
  const invocation = failedSignalInvocations.get(failureId);
  if (!invocation) {
    throw new Error(`Unknown failed signal invocation: ${failureId}`);
  }
  return emitApiSignal(invocation.signal, invocation.payload, invocation.options);
}

export function dismissSignalFailure(failureId: string): void {
  failedSignalInvocations.delete(failureId);
}

export async function emitApiSignal<TPayload, TResult>(
  key: ApiSignalKey,
  payload?: TPayload,
  options?: EmitSignalOptions,
): Promise<TResult> {
  const definition = signalRegistry.get(key);
  if (!definition) {
    const failureId = buildFailureId(key);
    failedSignalInvocations.set(failureId, {
      signal: key,
      payload,
      options,
    });
    const signalError = createSignalError(
      failureId,
      key,
      "[unregistered]",
      `Signal not registered: ${key}`,
      null,
    );
    pushSignalError(signalError);
    throw new Error(signalError.message);
  }

  const endpoint = resolveEndpoint(definition.endpoint, payload);
  try {
    const response = await executeSignalRequest<TPayload, TResult>(
      definition,
      endpoint,
      payload,
      options,
    );
    publishHtmlFrameIfNeeded(
      undefined,
      key,
      endpoint,
      response.contentType,
      response.status,
      response.data,
    );
    return response.data;
  } catch (error) {
    const failureId = buildFailureId(key);
    failedSignalInvocations.set(failureId, {
      signal: key,
      payload,
      options,
    });

    if (error instanceof HttpResponseError) {
      publishHtmlFrameIfNeeded(
        failureId,
        key,
        endpoint,
        error.contentType,
        error.statusCode,
        error.responseText,
      );
    }
    const detail =
      error instanceof HttpResponseError && isHtmlContentType(error.contentType)
        ? `${error.statusCode}: API unavailable`
        : "Signal request failed";
    const signalError = createSignalError(
      failureId,
      key,
      endpoint,
      detail,
      error instanceof HttpResponseError ? error.contentType : null,
    );
    pushSignalError(signalError);
    throw error;
  }
}

async function executeSignalRequest<TPayload, TResult>(
  definition: AnySignalDefinition,
  endpoint: string,
  payload: TPayload | undefined,
  options: EmitSignalOptions | undefined,
) {
  const timeoutMs = options?.timeoutMs ?? definition.timeoutMs;
  const mergedHeaders = {
    ...(definition.headers || {}),
    ...(options?.headers || {}),
  };

  switch (definition.method) {
    case "GET":
      return typeof timeoutMs === "number"
        ? apiWithTimeoutDetailed<TResult>(endpoint, timeoutMs)
        : apiDetailed<TResult>(endpoint);
    case "DELETE":
      return apiDeleteDetailed<TResult>(endpoint, timeoutMs, mergedHeaders);
    case "POST":
      return apiPostDetailed<TPayload, TResult>(
        endpoint,
        (payload || {}) as TPayload,
        timeoutMs,
        mergedHeaders,
      );
    default:
      throw new Error(`Unsupported signal method: ${String(definition.method)}`);
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
  id: string,
  signal: ApiSignalKey,
  endpoint: string,
  message: string,
  contentType?: string | null,
): ApiSignalError {
  const timestamp = new Date().toISOString();
  const statusCode = extractHttpStatusCode(message);
  return {
    id,
    signal,
    endpoint,
    message,
    statusCode,
    contentType,
    timestamp,
  };
}

function createSignalHtmlFrame(
  id: string,
  failureId: string | undefined,
  signal: ApiSignalKey,
  endpoint: string,
  html: string,
  statusCode?: number,
): ApiSignalHtmlFrame {
  const timestamp = new Date().toISOString();
  return {
    id,
    failureId,
    signal,
    endpoint,
    html,
    statusCode,
    timestamp,
  };
}

function extractHttpStatusCode(message: string): number | undefined {
  const match = /^([1-5]\d{2}):/.exec((message || "").trim());
  if (!match) {
    return undefined;
  }
  const statusCode = Number.parseInt(match[1], 10);
  return Number.isFinite(statusCode) ? statusCode : undefined;
}

function isHtmlContentType(contentType: string | null | undefined): boolean {
  return (contentType || "").toLowerCase().includes("text/html");
}

function publishHtmlFrameIfNeeded(
  failureId: string | undefined,
  signal: ApiSignalKey,
  endpoint: string,
  contentType: string | null | undefined,
  statusCode: number | undefined,
  payload: unknown,
): void {
  if (!isHtmlContentType(contentType)) {
    return;
  }
  const html = typeof payload === "string" ? payload : "";
  if (!html.trim()) {
    return;
  }
  pushSignalHtmlFrame(
    createSignalHtmlFrame(
      `${new Date().toISOString()}:${signal}:html:${Math.random().toString(36).slice(2, 8)}`,
      failureId,
      signal,
      endpoint,
      html,
      statusCode,
    ),
  );
}

function buildFailureId(signal: ApiSignalKey): string {
  return `${new Date().toISOString()}:${signal}:${Math.random().toString(36).slice(2, 8)}`;
}

function pushSignalError(error: ApiSignalError): void {
  console.error(
    `[signal ${error.signal}] ${error.message}`,
    {
      endpoint: error.endpoint,
      statusCode: error.statusCode,
      contentType: error.contentType,
      timestamp: error.timestamp,
    },
  );
  signalErrorHistory = [...signalErrorHistory, error].slice(
    -MAX_SIGNAL_ERROR_HISTORY,
  );
  for (const listener of signalErrorListeners) {
    listener(error);
  }
}

function pushSignalHtmlFrame(frame: ApiSignalHtmlFrame): void {
  signalHtmlFrameHistory = [...signalHtmlFrameHistory, frame].slice(
    -MAX_SIGNAL_HTML_FRAME_HISTORY,
  );
  for (const listener of signalHtmlFrameListeners) {
    listener(frame);
  }
}
