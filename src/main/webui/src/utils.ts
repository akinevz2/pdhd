/** API request timeout in milliseconds. */
export const API_TIMEOUT_MS = 10000;

/** Timeout for long-running AI/LLM calls (e.g. folder summarisation). */
export const CHAT_TIMEOUT_MS = 120_000;

/**
 * Normalises a file path for consistent cross-platform comparison:
 * converts back-slashes to forward-slashes and strips leading slashes,
 * then lowercases the result.
 */
export function normalize(path: string): string {
  return path.replace(/\\/g, "/").replace(/^\/+/, "").toLowerCase();
}

/** Returns `true` when the path points to a common image file. */
export function isImagePath(path: string): boolean {
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(path);
}

/** Returns `true` when the path points to a PDF document. */
export function isPdfPath(path: string): boolean {
  return /\.pdf$/i.test(path);
}

/** Builds the raw-file endpoint URL for a project-relative file path. */
export function rawFileUrl(
  projectId: number,
  entryUuid: string,
): string {
  return `/api/fs/file/raw?projectId=${encodeURIComponent(projectId)}&entryUuid=${encodeURIComponent(entryUuid)}`;
}

/**
 * Builds the URL for the raw-image endpoint so the browser can render
 * the image directly without fetching base64 content.
 */
export function rawImageUrl(
  projectId: number,
  entryUuid: string,
): string {
  return rawFileUrl(projectId, entryUuid);
}

/** Returns true when a repository URL can be opened safely in the browser. */
export function isBrowsableRepoUrl(url?: string | null): boolean {
  if (!url) {
    return false;
  }

  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

/** Opens an external URL in a new browser tab. */
export function openExternalUrl(url: string): void {
  window.open(url, "_blank", "noopener,noreferrer");
}
