import { useCallback, useState } from "react";
import { api, apiPost } from "../api";
import type { OllamaModelsResponse, OllamaSettings } from "../types";

export function useMenuPanels() {
  const [ollamaOpen, setOllamaOpen] = useState(false);
  const [ollamaForm, setOllamaForm] = useState<OllamaSettings | null>(null);
  const [ollamaLoading, setOllamaLoading] = useState(false);
  const [ollamaSaving, setOllamaSaving] = useState(false);
  const [ollamaError, setOllamaError] = useState<string | null>(null);
  const [availableModels, setAvailableModels] = useState<string[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);

  const [promptOpen, setPromptOpen] = useState(false);
  const [promptDraft, setPromptDraft] = useState("");
  const [promptDefault, setPromptDefault] = useState("");
  const [toolPromptDraft, setToolPromptDraft] = useState("");
  const [toolPromptDefault, setToolPromptDefault] = useState("");
  const [promptSaving, setPromptSaving] = useState(false);
  const [promptError, setPromptError] = useState<string | null>(null);

  const [debugOpen, setDebugOpen] = useState(false);

  const openOllamaConfig = useCallback(async () => {
    setOllamaLoading(true);
    setOllamaError(null);
    try {
      const data = await api<OllamaSettings>("/api/menu/ollama");
      setOllamaForm(data);
      setAvailableModels([]);
      setOllamaOpen(true);
    } catch {
      setOllamaError("Failed to load Ollama settings.");
    } finally {
      setOllamaLoading(false);
    }
  }, []);

  const fetchModels = useCallback(async (baseUrl: string) => {
    setModelsLoading(true);
    try {
      const data = await api<OllamaModelsResponse>(
        `/api/menu/ollama/models?baseUrl=${encodeURIComponent(baseUrl)}`,
      );
      setAvailableModels(data.models || []);
    } catch {
      setAvailableModels([]);
    } finally {
      setModelsLoading(false);
    }
  }, []);

  const saveOllamaConfig = useCallback(async () => {
    if (!ollamaForm) return;
    setOllamaSaving(true);
    setOllamaError(null);
    try {
      await apiPost<OllamaSettings, OllamaSettings>(
        "/api/menu/ollama",
        ollamaForm,
      );
      setOllamaOpen(false);
    } catch {
      setOllamaError("Failed to save settings.");
    } finally {
      setOllamaSaving(false);
    }
  }, [ollamaForm]);

  const openSystemPrompt = useCallback(async () => {
    setPromptError(null);
    try {
      const data = await api<OllamaSettings>("/api/menu/ollama");
      setPromptDraft(data.systemPrompt || data.defaultSystemPrompt);
      setPromptDefault(data.defaultSystemPrompt);
      setToolPromptDraft(data.toolSystemPrompt || data.defaultToolSystemPrompt);
      setToolPromptDefault(data.defaultToolSystemPrompt);
      setPromptOpen(true);
    } catch {
      setPromptError("Failed to load settings.");
    }
  }, []);

  const saveSystemPrompt = useCallback(async () => {
    setPromptSaving(true);
    setPromptError(null);
    try {
      const current = await api<OllamaSettings>("/api/menu/ollama");
      await apiPost<OllamaSettings, OllamaSettings>("/api/menu/ollama", {
        ...current,
        systemPrompt: promptDraft,
        toolSystemPrompt: toolPromptDraft,
      });
      setPromptOpen(false);
    } catch {
      setPromptError("Failed to save system prompt.");
    } finally {
      setPromptSaving(false);
    }
  }, [promptDraft, toolPromptDraft]);

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
    ollamaOpen,
    setOllamaOpen,
    ollamaForm,
    setOllamaForm,
    ollamaLoading,
    ollamaSaving,
    ollamaError,
    availableModels,
    modelsLoading,
    promptOpen,
    setPromptOpen,
    promptDraft,
    setPromptDraft,
    promptDefault,
    toolPromptDraft,
    setToolPromptDraft,
    toolPromptDefault,
    promptSaving,
    promptError,
    debugOpen,
    setDebugOpen,
    openOllamaConfig,
    fetchModels,
    saveOllamaConfig,
    openSystemPrompt,
    saveSystemPrompt,
    handleExit,
  };
}
