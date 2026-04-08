import { useCallback, useMemo, useState } from "react";
import { api, apiPost } from "../../api";
import type {
  OllamaModelsResponse,
  OllamaRuntimeStatus,
  OllamaSettingField,
  OllamaSettings,
  PullProgressStatus,
} from "../../types";

export type ConfigurationProvider = "OLLAMA";
export type ConfigurationForm = Record<
  string,
  string | number | boolean | null
>;

const DEFAULT_PROVIDER: ConfigurationProvider = "OLLAMA";
type RuntimeProviderMode = "EXTERNAL" | "INTERNAL";

export function useConfigurationMenus() {
  const [configOpen, setConfigOpen] = useState(false);
  const [configForm, setConfigForm] = useState<ConfigurationForm | null>(null);
  const [configFields, setConfigFields] = useState<OllamaSettingField[]>([]);
  const [configLoading, setConfigLoading] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);
  const [configError, setConfigError] = useState<string | null>(null);
  const [availableModels, setAvailableModels] = useState<string[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);
  const [pullProgress, setPullProgress] = useState<PullProgressStatus | null>(
    null,
  );
  const [runtimeStatus, setRuntimeStatus] =
    useState<OllamaRuntimeStatus | null>(null);
  const [runtimeSwitching, setRuntimeSwitching] = useState(false);

  const currentProvider = useMemo<ConfigurationProvider>(
    () => DEFAULT_PROVIDER,
    [],
  );

  const supportsManagedModels = currentProvider === "OLLAMA";

  const openConfiguration = useCallback(async () => {
    setConfigLoading(true);
    setConfigError(null);
    try {
      const [data, status] = await Promise.all([
        api<OllamaSettings>("/api/menu/ollama"),
        api<OllamaRuntimeStatus>("/api/menu/ollama/status"),
      ]);
      setConfigForm((data.settings ?? null) as ConfigurationForm | null);
      setConfigFields(data.settingFields ?? []);
      setRuntimeStatus(status);
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
    setPullProgress(null);
    try {
      await new Promise<void>((resolve, reject) => {
        const source = new EventSource(
          `/api/menu/ollama/models/pull/stream?modelName=${encodeURIComponent(modelName)}`,
        );
        let lastStatus: PullProgressStatus | null = null;

        source.onmessage = (event) => {
          try {
            const s = JSON.parse(event.data as string) as PullProgressStatus;
            lastStatus = s;
            setPullProgress(s);
          } catch {
            // ignore parse errors
          }
        };

        source.onerror = () => {
          source.close();
          if (lastStatus?.status === "success") {
            resolve();
          } else {
            reject(
              new Error(
                lastStatus?.status
                  ? `Pull ended with status: ${lastStatus.status}`
                  : "Pull failed or connection lost",
              ),
            );
          }
        };
      });

      const data = await api<OllamaModelsResponse>("/api/menu/ollama/models");
      setAvailableModels(data.models ?? []);
    } catch (error) {
      setConfigError(
        error instanceof Error ? error.message : "Failed to pull model.",
      );
    } finally {
      setPullProgress(null);
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

  const switchRuntimeProvider = useCallback(
    async (provider: RuntimeProviderMode) => {
      setRuntimeSwitching(true);
      setConfigError(null);
      try {
        const status = await apiPost<
          { provider: RuntimeProviderMode; baseUrl?: string },
          OllamaRuntimeStatus
        >("/api/menu/ollama/runtime/provider", {
          provider,
          baseUrl:
            provider === "EXTERNAL"
              ? String(configForm?.baseUrl ?? "")
              : undefined,
        });
        setRuntimeStatus(status);
      } catch (error) {
        setConfigError(
          error instanceof Error
            ? error.message
            : "Failed to switch runtime provider.",
        );
      } finally {
        setRuntimeSwitching(false);
      }
    },
    [configForm],
  );

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
    pullProgress,
    runtimeStatus,
    runtimeSwitching,
    openConfiguration,
    refreshModels,
    pullModel,
    deleteModel,
    saveConfiguration,
    switchRuntimeProvider,
  };
}
