import { useCallback, useMemo, useState } from "react";
import { api, apiPost } from "../../api";
import type {
  OllamaModelsResponse,
  OllamaSettingField,
  OllamaSettings,
} from "../../types";

export type ConfigurationProvider = "OLLAMA" | "OPENAI";
export type ConfigurationForm = Record<
  string,
  string | number | boolean | null
>;

const DEFAULT_PROVIDER: ConfigurationProvider = "OLLAMA";

export function useConfigurationMenus() {
  const [configOpen, setConfigOpen] = useState(false);
  const [configForm, setConfigForm] = useState<ConfigurationForm | null>(null);
  const [configFields, setConfigFields] = useState<OllamaSettingField[]>([]);
  const [configLoading, setConfigLoading] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);
  const [configError, setConfigError] = useState<string | null>(null);
  const [availableModels, setAvailableModels] = useState<string[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);

  const currentProvider = useMemo<ConfigurationProvider>(() => {
    const provider = String(configForm?.provider ?? DEFAULT_PROVIDER)
      .trim()
      .toUpperCase();
    return provider === "OPENAI" ? "OPENAI" : "OLLAMA";
  }, [configForm]);

  const supportsManagedModels = currentProvider === "OLLAMA";

  const openConfiguration = useCallback(async () => {
    setConfigLoading(true);
    setConfigError(null);
    try {
      const data = await api<OllamaSettings>("/api/menu/ollama");
      setConfigForm((data.settings ?? null) as ConfigurationForm | null);
      setConfigFields(data.settingFields ?? []);
      setAvailableModels([]);
      setConfigOpen(true);
    } catch {
      setConfigError("Failed to load configuration.");
    } finally {
      setConfigLoading(false);
    }
  }, []);

  const refreshModels = useCallback(async () => {
    setModelsLoading(true);
    try {
      const data = await api<OllamaModelsResponse>("/api/menu/ollama/models");
      setAvailableModels(data.models ?? []);
    } catch {
      setAvailableModels([]);
    } finally {
      setModelsLoading(false);
    }
  }, []);

  const pullModel = useCallback(async (modelName: string) => {
    setModelsLoading(true);
    setConfigError(null);
    try {
      const data = await apiPost<{ modelName: string }, OllamaModelsResponse>(
        "/api/menu/ollama/models/pull",
        { modelName },
      );
      setAvailableModels(data.models ?? []);
    } catch (error) {
      setConfigError(
        error instanceof Error ? error.message : "Failed to pull model.",
      );
    } finally {
      setModelsLoading(false);
    }
  }, []);

  const deleteModel = useCallback(async (modelName: string) => {
    setModelsLoading(true);
    setConfigError(null);
    try {
      const data = await apiPost<{ modelName: string }, OllamaModelsResponse>(
        "/api/menu/ollama/models/delete",
        { modelName },
      );
      setAvailableModels(data.models ?? []);
      setConfigForm((current) => {
        if (!current || current.modelName !== modelName) {
          return current;
        }
        return {
          ...current,
          modelName: data.models?.[0] ?? "",
        };
      });
    } catch (error) {
      setConfigError(
        error instanceof Error ? error.message : "Failed to delete model.",
      );
    } finally {
      setModelsLoading(false);
    }
  }, []);

  const saveConfiguration = useCallback(async () => {
    if (!configForm) {
      return;
    }
    setConfigSaving(true);
    setConfigError(null);
    try {
      const response = await apiPost<
        { settings: ConfigurationForm },
        OllamaSettings
      >("/api/menu/ollama", { settings: configForm });
      setConfigForm((response.settings ?? configForm) as ConfigurationForm);
      setConfigFields(response.settingFields ?? []);
      setConfigOpen(false);
    } catch (error) {
      setConfigError(
        error instanceof Error
          ? error.message
          : "Failed to save configuration.",
      );
    } finally {
      setConfigSaving(false);
    }
  }, [configForm]);

  return {
    configOpen,
    setConfigOpen,
    configForm,
    setConfigForm,
    configFields,
    configLoading,
    configSaving,
    configError,
    currentProvider,
    supportsManagedModels,
    availableModels,
    modelsLoading,
    openConfiguration,
    refreshModels,
    pullModel,
    deleteModel,
    saveConfiguration,
  };
}
