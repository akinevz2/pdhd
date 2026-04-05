import { useCallback, useState } from "react";
import { apiPost } from "../api";

export function useMenuPanels() {
  const [debugOpen, setDebugOpen] = useState(false);

  const handleExit = useCallback(async () => {
    if (!window.confirm("Exit the application?")) return;
    try {
      await apiPost<Record<string, never>, unknown>("/api/menu/exit", {});
    } catch {
      // server may already be shutting down
    }

    // Best-effort tab close: browsers may block this unless the tab was script-opened,
    // so we also attempt a self-target close path.
    window.setTimeout(() => {
      try {
        window.open("", "_self");
      } catch {
        // ignore
      }
      try {
        window.close();
      } catch {
        // ignore
      }
    }, 120);
  }, []);

  return {
    debugOpen,
    setDebugOpen,
    handleExit,
  };
}
