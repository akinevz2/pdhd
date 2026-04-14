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

function resolveConfigBaseUrl(
  configForm: ConfigurationForm | null,
): string | null {
  const candidate = configForm?.baseUrl;
  if (candidate === null || candidate === undefined) {
    return null;
  }
  const text = String(candidate).trim();
  return text.length > 0 ? text : null;
}

export function useConfigurationMenus() {
  const [configOpen, setConfigOpen] = useState(false);
  const [configForm, setConfigForm] = useState<ConfigurationForm | null>(null);
  const [configFields, setConfigFields] = useState<OllamaSettingField[]>([]);
  const [configLoading, setConfigLoading] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);
  const [availableModels, setAvailableModels] = useState<string[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);
  const [pullProgress, setPullProgress] = useState<PullProgressStatus | null>(
    null,
  );
  const [runtimeStatus, setRuntimeStatus] =
    useState<OllamaRuntimeStatus | null>(null);

  const currentProvider = useMemo<ConfigurationProvider>(
    () => DEFAULT_PROVIDER,
    [],
  );

  const supportsManagedModels = currentProvider === "OLLAMA";

  const openConfiguration = useCallback(async () => {
    setConfigLoading(true);
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
      // errors are surfaced by signal failure state
    } finally {
      setConfigLoading(false);
    }
  }, []);

  const refreshModels = useCallback(async () => {
    setModelsLoading(true);
    try {
      const baseUrl = resolveConfigBaseUrl(configForm);
      const query = baseUrl
        ? `?baseUrl=${encodeURIComponent(baseUrl)}`
        : "";
      const data = await api<OllamaModelsResponse>(`/api/menu/ollama/models${query}`);
      setAvailableModels(data.models ?? []);
    } catch {
      setAvailableModels([]);
    } finally {
      setModelsLoading(false);
    }
  }, [configForm]);

  const pullModel = useCallback(async (modelName: string) => {
    setModelsLoading(true);
    setPullProgress(null);
    try {
      await new Promise<void>((resolve, reject) => {
        const baseUrl = resolveConfigBaseUrl(configForm);
        const baseUrlQuery = baseUrl
          ? `&baseUrl=${encodeURIComponent(baseUrl)}`
          : "";
        const source = new EventSource(
          `/api/menu/ollama/models/pull/stream?modelName=${encodeURIComponent(modelName)}${baseUrlQuery}`,
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

      const baseUrl = resolveConfigBaseUrl(configForm);
      const query = baseUrl
        ? `?baseUrl=${encodeURIComponent(baseUrl)}`
        : "";
      const data = await api<OllamaModelsResponse>(`/api/menu/ollama/models${query}`);
      setAvailableModels(data.models ?? []);
    } catch {
      // errors are surfaced by signal failure state
    } finally {
      setPullProgress(null);
      setModelsLoading(false);
    }
  }, [configForm]);

  const deleteModel = useCallback(async (modelName: string) => {
    setModelsLoading(true);
    try {
      const data = await apiPost<{ modelName: string; baseUrl?: string }, OllamaModelsResponse>(
        "/api/menu/ollama/models/delete",
        {
          modelName,
          baseUrl: resolveConfigBaseUrl(configForm) ?? undefined,
        },
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
    } catch {
      // errors are surfaced by signal failure state
    } finally {
      setModelsLoading(false);
    }
  }, [configForm]);

  const saveConfiguration = useCallback(async () => {
    if (!configForm) {
      return;
    }
    setConfigSaving(true);
    try {
      const response = await apiPost<
        { settings: ConfigurationForm },
        OllamaSettings
      >("/api/menu/ollama", { settings: configForm });
      setConfigForm((response.settings ?? configForm) as ConfigurationForm);
      setConfigFields(response.settingFields ?? []);
      setConfigOpen(false);
    } catch {
      // errors are surfaced by signal failure state
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
    configError: null,
    currentProvider,
    supportsManagedModels,
    availableModels,
    modelsLoading,
    pullProgress,
    runtimeStatus,
    openConfiguration,
    refreshModels,
    pullModel,
    deleteModel,
    saveConfiguration,
  };
}
