/** Milliseconds between background polls for activity and CWD. */
export const POLL_MS = 2000;

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

/**
 * Builds the URL for the raw-image endpoint so the browser can render
 * the image directly without fetching base64 content.
 */
export function rawImageUrl(
  projectDirectory: string,
  relativePath: string,
): string {
  const absolutePath =
    projectDirectory.replace(/\\/g, "/") + "/" + relativePath;
  return `/api/fs/file/raw?path=${encodeURIComponent(absolutePath)}`;
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
