import { API_TIMEOUT_MS } from "./utils";

/**
 * Makes an authenticated GET request and parses the JSON response.
 * Throws on non-2xx status codes.
 */
export async function api<T>(url: string): Promise<T> {
  return fetchJson<T>(url, { method: "GET" });
}

/**
 * Makes a POST request with a JSON body and parses the JSON response.
 * Returns an empty object for 204 No Content responses.
 */
export async function apiPost<TReq, TRes>(
  url: string,
  body: TReq,
  timeoutMs?: number,
): Promise<TRes> {
  const response = await fetchWithTimeout(
    url,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
    timeoutMs,
  );
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  if (response.status === 204) {
    return {} as TRes;
  }
  return (await response.json()) as TRes;
}

async function fetchJson<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetchWithTimeout(url, init);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return (await response.json()) as T;
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
