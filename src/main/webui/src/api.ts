import { API_TIMEOUT_MS } from "./utils";

/**
 * Makes an authenticated GET request and parses the JSON response.
 * Throws on non-2xx status codes.
 */
export async function api<T>(url: string): Promise<T> {
  return fetchJson<T>(url, { method: "GET" });
}

/**
 * Makes an authenticated GET request with an optional timeout override.
 */
export async function apiWithTimeout<T>(
  url: string,
  timeoutMs?: number,
): Promise<T> {
  return fetchJson<T>(url, { method: "GET" }, timeoutMs);
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
  if (response.status === 204) {
    return {} as TRes;
  }
  return (await response.json()) as TRes;
}

async function fetchJson<T>(
  url: string,
  init: RequestInit,
  timeoutMs?: number,
): Promise<T> {
  const response = await fetchWithTimeout(url, init, timeoutMs);
  if (!response.ok) {
    throw await buildHttpError(response);
  }
  return (await response.json()) as T;
}

async function buildHttpError(response: Response): Promise<Error> {
  const fallback = `${response.status} ${response.statusText}`.trim();
  try {
    const text = (await response.text()).trim();
    if (!text) {
      return new Error(fallback || "HTTP request failed");
    }

    try {
      const parsed = JSON.parse(text) as {
        message?: string;
        details?: string;
        error?: string;
      };
      const detail = (
        parsed.message ||
        parsed.details ||
        parsed.error ||
        ""
      ).trim();
      if (detail) {
        return new Error(`${fallback}: ${detail}`);
      }
    } catch {
      // Non-JSON error body; use raw text.
    }

    return new Error(`${fallback}: ${text}`);
  } catch {
    return new Error(fallback || "HTTP request failed");
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
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error(`Request timed out after ${timeoutMs}ms`);
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
  }
}
