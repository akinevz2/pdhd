import { useMemo } from "react";
import type { ToolActivityItem, WindowState } from "../types";
import { normalize } from "../utils";

/**
 * Derives the set of highlighted file paths for each open project window.
 *
 * A file path is highlighted when it appears in the `requestedFiles` of any
 * recent tool-activity event from the assistant. Additionally, all parent
 * directories of highlighted files are also highlighted, allowing folder
 * navigation to show which parts of the project tree were involved in recent
 * tool calls.
 *
 * The returned map is keyed by project directory string so that each
 * {@link ProjectWindow} can quickly look up its own set without iterating
 * the full activity list.
 */
export function useHighlightedFiles(
  activityItems: ToolActivityItem[],
  windows: WindowState[],
): Map<string, Set<string>> {
  return useMemo(() => {
    const out = new Map<string, Set<string>>();
    const openProjects = new Set(windows.map((w) => w.project.directory));

    for (const event of activityItems) {
      for (const requested of event.requestedFiles || []) {
        const normalised = normalize(requested);
        for (const projectDirectory of openProjects) {
          if (!out.has(projectDirectory)) {
            out.set(projectDirectory, new Set<string>());
          }
          const highlighted = out.get(projectDirectory)!;

          // Add the file itself
          highlighted.add(normalised);

          // Add all parent directories of this file
          const parts = normalised.split("/");
          for (let i = 1; i < parts.length; i++) {
            const dirPath = parts.slice(0, i).join("/");
            highlighted.add(dirPath);
          }
        }
      }
    }

    return out;
  }, [activityItems, windows]);
}
