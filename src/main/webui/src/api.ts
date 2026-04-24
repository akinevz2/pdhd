import { API_TIMEOUT_MS } from "./utils";

export type ApiResponseEnvelope<T> = {
  data: T;
  contentType: string | null;
  status: number;
};

export class HttpResponseError extends Error {
  statusCode: number;
  contentType: string | null;
  responseText: string;

  constructor(
    statusCode: number,
    message: string,
    contentType: string | null,
    responseText: string,
  ) {
    super(`${statusCode}: ${message}`);
    this.name = "HttpResponseError";
    this.statusCode = statusCode;
    this.contentType = contentType;
    this.responseText = responseText;
  }
}

export type TextStreamCallbacks = {
  onChunk: (chunk: string) => void;
  onDone: () => void;
  onError: (message: string) => void;
};

function resolveRequestUrl(url: string): string {
  if (!url || !url.startsWith("/")) {
    return url;
  }

  // Treat leading-slash URLs as root-absolute API paths.
  // Prefixing with the current pathname breaks API calls when the SPA route changes.
  return url;
}

/**
 * Makes an authenticated GET request and parses the JSON response.
 * Throws on non-2xx status codes.
 */
export async function api<T>(url: string): Promise<T> {
  const response = await fetchJsonDetailed<T>(url, { method: "GET" });
  return response.data;
}

/**
 * Makes an authenticated GET request with an optional timeout override.
 */
export async function apiWithTimeout<T>(
  url: string,
  timeoutMs?: number,
): Promise<T> {
  const response = await fetchJsonDetailed<T>(url, { method: "GET" }, timeoutMs);
  return response.data;
}

export async function apiDetailed<T>(url: string): Promise<ApiResponseEnvelope<T>> {
  return fetchJsonDetailed<T>(url, { method: "GET" });
}

export async function apiWithTimeoutDetailed<T>(
  url: string,
  timeoutMs?: number,
): Promise<ApiResponseEnvelope<T>> {
  return fetchJsonDetailed<T>(url, { method: "GET" }, timeoutMs);
}

/**
 * Makes a POST request with a JSON body and parses the JSON response.
 * Returns an empty object for 204 No Content responses.
 */
export async function apiPost<TReq, TRes>(
  url: string,
  body: TReq,
  timeoutMs?: number,
  extraHeaders?: Record<string, string>,
): Promise<TRes> {
  const response = await apiPostDetailed<TReq, TRes>(
    url,
    body,
    timeoutMs,
    extraHeaders,
  );
  return response.data;
}

export async function apiPostDetailed<TReq, TRes>(
  url: string,
  body: TReq,
  timeoutMs?: number,
  extraHeaders?: Record<string, string>,
): Promise<ApiResponseEnvelope<TRes>> {
  const response = await fetchWithTimeout(
    url,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(extraHeaders || {}) },
      body: JSON.stringify(body),
    },
    timeoutMs,
  );
  if (!response.ok) {
    throw await buildHttpError(response);
  }
  return readResponseBody<TRes>(response);
}

/**
 * Makes a PUT request with a JSON body and parses the JSON response.
 * Returns an empty object for 204 No Content responses.
 */
export async function apiPut<TReq, TRes>(
  url: string,
  body: TReq,
  timeoutMs?: number,
  extraHeaders?: Record<string, string>,
): Promise<TRes> {
  const response = await apiPutDetailed<TReq, TRes>(
    url,
    body,
    timeoutMs,
    extraHeaders,
  );
  return response.data;
}

export async function apiPutDetailed<TReq, TRes>(
  url: string,
  body: TReq,
  timeoutMs?: number,
  extraHeaders?: Record<string, string>,
): Promise<ApiResponseEnvelope<TRes>> {
  const response = await fetchWithTimeout(
    url,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json", ...(extraHeaders || {}) },
      body: JSON.stringify(body),
    },
    timeoutMs,
  );
  if (!response.ok) {
    throw await buildHttpError(response);
  }
  return readResponseBody<TRes>(response);
}

/**
 * Makes a DELETE request and parses the JSON response.
 * Returns an empty object for 204 No Content responses.
 */
export async function apiDelete<TRes>(
  url: string,
  timeoutMs?: number,
  extraHeaders?: Record<string, string>,
): Promise<TRes> {
  const response = await apiDeleteDetailed<TRes>(url, timeoutMs, extraHeaders);
  return response.data;
}

export async function apiDeleteDetailed<TRes>(
  url: string,
  timeoutMs?: number,
  extraHeaders?: Record<string, string>,
  body?: unknown,
): Promise<ApiResponseEnvelope<TRes>> {
  const hasBody = typeof body !== "undefined";
  const headers = hasBody
    ? { "Content-Type": "application/json", ...(extraHeaders || {}) }
    : extraHeaders || {};
  const response = await fetchWithTimeout(
    url,
    {
      method: "DELETE",
      headers,
      ...(hasBody ? { body: JSON.stringify(body) } : {}),
    },
    timeoutMs,
  );
  if (!response.ok) {
    throw await buildHttpError(response);
  }
  return readResponseBody<TRes>(response);
}

/**
 * Makes a POST request and streams text responses.
 * Supports SSE (`text/event-stream`) and plain-text chunked responses.
 */
export async function apiPostTextStream<TReq>(
  url: string,
  body: TReq,
  callbacks: TextStreamCallbacks,
  timeoutMs = API_TIMEOUT_MS,
): Promise<void> {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(resolveRequestUrl(url), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: controller.signal,
    });

    if (!response.ok) {
      throw await buildHttpError(response);
    }

    if (!response.body) {
      callbacks.onDone();
      return;
    }

    const isSse =
      response.headers
        .get("content-type")
        ?.toLowerCase()
        .includes("text/event-stream") ?? false;

    const reader = response.body.getReader();
    const decoder = new TextDecoder();

    if (isSse) {
      let buffer = "";
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true }).replace(/\r\n?/g, "\n");

        let boundary = buffer.indexOf("\n\n");
        while (boundary >= 0) {
          const event = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          emitSseData(event, callbacks);
          boundary = buffer.indexOf("\n\n");
        }
      }

      const tail = buffer.trim();
      if (tail) {
        emitSseData(tail, callbacks);
      }
    } else {
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        const chunk = decoder.decode(value, { stream: true });
        if (chunk) {
          callbacks.onChunk(chunk);
        }
      }
    }

    callbacks.onDone();
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      callbacks.onError(`Request timed out after ${timeoutMs}ms`);
      callbacks.onDone();
      return;
    }
    callbacks.onError("Request failed");
    callbacks.onDone();
  } finally {
    window.clearTimeout(timeoutId);
  }
}

function emitSseData(event: string, callbacks: TextStreamCallbacks): void {
  const dataLines: string[] = [];
  for (const line of event.split("\n")) {
    if (line.startsWith("data:")) {
      // SSE permits a single optional space after "data:"; preserve all other whitespace.
      let value = line.slice(5);
      if (value.startsWith(" ")) {
        value = value.slice(1);
      }
      dataLines.push(value);
    }
  }

  const payload = dataLines.join("\n");
  if (!payload || payload === "[DONE]") {
    return;
  }
  callbacks.onChunk(payload);
}

async function fetchJsonDetailed<T>(
  url: string,
  init: RequestInit,
  timeoutMs?: number,
): Promise<ApiResponseEnvelope<T>> {
  const response = await fetchWithTimeout(url, init, timeoutMs);
  if (!response.ok) {
    throw await buildHttpError(response);
  }
  return readResponseBody<T>(response);
}

async function readResponseBody<T>(response: Response): Promise<ApiResponseEnvelope<T>> {
  const contentType = response.headers.get("content-type");
  if (response.status === 204) {
    return {
      data: {} as T,
      contentType,
      status: response.status,
    };
  }

  if (contentType?.toLowerCase().includes("text/html")) {
    return {
      data: (await response.text()) as T,
      contentType,
      status: response.status,
    };
  }

  if (contentType?.toLowerCase().startsWith("text/")) {
    return {
      data: (await response.text()) as T,
      contentType,
      status: response.status,
    };
  }

  return {
    data: (await response.json()) as T,
    contentType,
    status: response.status,
  };
}

async function buildHttpError(response: Response): Promise<Error> {
  const statusCode = response.status;
  const statusText = response.statusText.trim();
  const contentType = response.headers.get("content-type");
  try {
    const text = (await response.text()).trim();
    const message = text || statusText || "HTTP request failed";
    return new HttpResponseError(statusCode, message, contentType, text);
  } catch {
    const message = statusText || "HTTP request failed";
    return new HttpResponseError(statusCode, message, contentType, "");
  }
}

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs = API_TIMEOUT_MS,
): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(resolveRequestUrl(url), {
      ...init,
      signal: controller.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error(`Request timed out after ${timeoutMs}ms`);
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
  }
}
