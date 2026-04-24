import { useCallback, useState } from "react";
import { apiPost } from "../api";

export function useMenuPanels() {
  const [debugOpen, setDebugOpen] = useState(false);
  const [pendingExitConfirmation, setPendingExitConfirmation] = useState(false);

  const performExit = useCallback(async () => {
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

  const handleExit = useCallback(async () => {
    if (!pendingExitConfirmation) {
      setPendingExitConfirmation(true);
      return;
    }
    setPendingExitConfirmation(false);
    await performExit();
  }, [pendingExitConfirmation, performExit]);

  const dismissExitConfirmation = useCallback(() => {
    setPendingExitConfirmation(false);
  }, []);

  return {
    debugOpen,
    setDebugOpen,
    handleExit,
    pendingExitConfirmation,
    dismissExitConfirmation,
  };
}
