import type { Dispatch, ReactNode, SetStateAction } from "react";
import type { OllamaSettingField } from "../../types";
import type { ConfigurationForm, ConfigurationProvider } from "./useConfigurationMenus";

type ConfigurationMenuButtonsProps = {
  configLoading: boolean;
  onOpenConfiguration: () => Promise<void>;
};

type ConfigurationModalsProps = {
  configOpen: boolean;
  configForm: ConfigurationForm | null;
  configFields: OllamaSettingField[];
  configError: string | null;
  configSaving: boolean;
  currentProvider: ConfigurationProvider;
  supportsManagedModels: boolean;
  availableModels: string[];
  modelsLoading: boolean;
  setConfigOpen: Dispatch<SetStateAction<boolean>>;
  setConfigForm: Dispatch<SetStateAction<ConfigurationForm | null>>;
  refreshModels: () => Promise<void>;
  pullModel: (modelName: string) => Promise<void>;
  deleteModel: (modelName: string) => Promise<void>;
  saveConfiguration: () => Promise<void>;
};

const PROVIDERS: ConfigurationProvider[] = ["OLLAMA", "OPENAI"];
const FIELD_PRIORITY: Record<string, number> = {
  provider: 0,
  baseUrl: 1,
  modelName: 2,
};

export function ConfigurationMenuButtons({
  configLoading,
  onOpenConfiguration,
}: ConfigurationMenuButtonsProps) {
  return (
    <>
      <button
        className="menu-btn"
        onClick={() => {
          onOpenConfiguration().catch(() => {});
        }}
        disabled={configLoading}
      >
        {configLoading ? "Loading..." : "Configuration"}
      </button>
    </>
  );
}

export function ConfigurationModals({
  configOpen,
  configForm,
  configFields,
  configError,
  configSaving,
  currentProvider,
  supportsManagedModels,
  availableModels,
  modelsLoading,
  setConfigOpen,
  setConfigForm,
  refreshModels,
  pullModel,
  deleteModel,
  saveConfiguration,
}: ConfigurationModalsProps) {
  const fieldString = (key: string) => String(configForm?.[key] ?? "");
  const fieldNumber = (key: string) => {
    const raw = configForm?.[key];
    if (typeof raw === "number") return raw;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : 0;
  };
  const fieldBoolean = (key: string) => Boolean(configForm?.[key]);
  const orderedFields = [...configFields].sort(
    (left, right) => (FIELD_PRIORITY[left.key] ?? 100) - (FIELD_PRIORITY[right.key] ?? 100),
  );
  const selectedModel = fieldString("modelName");

  const renderField = (field: OllamaSettingField): ReactNode => {
    if (!configForm) {
      return null;
    }

    if (field.key === "provider") {
      return (
        <div className="hint-row">
          <select
            className="form-input"
            value={String(configForm.provider ?? "OLLAMA")}
            onChange={(event) =>
              setConfigForm({
                ...configForm,
                provider: event.target.value,
              })
            }
          >
            {PROVIDERS.map((provider) => (
              <option key={provider} value={provider}>
                {provider}
              </option>
            ))}
          </select>
          <span className="form-hint">Select the active runtime provider.</span>
        </div>
      );
    }

    if (field.inputType === "boolean") {
      return (
        <div className="hint-row">
          <input
            type="checkbox"
            checked={fieldBoolean(field.key)}
            onChange={(event) =>
              setConfigForm({
                ...configForm,
                [field.key]: event.target.checked,
              })
            }
          />
          {field.hint && <span className="form-hint">{field.hint}</span>}
        </div>
      );
    }

    if (field.modelField) {
      return (
        <div style={{ display: "grid", gap: 8 }}>
          {supportsManagedModels && availableModels.length > 0 ? (
            <select
              className="form-input"
              value={fieldString(field.key)}
              onChange={(event) =>
                setConfigForm({
                  ...configForm,
                  [field.key]: event.target.value,
                })
              }
            >
              {!availableModels.includes(fieldString(field.key)) && selectedModel && (
                <option value={fieldString(field.key)}>{fieldString(field.key)}</option>
              )}
              {availableModels.map((model) => (
                <option key={model} value={model}>
                  {model}
                </option>
              ))}
            </select>
          ) : (
            <input
              className="form-input"
              value={fieldString(field.key)}
              onChange={(event) =>
                setConfigForm({
                  ...configForm,
                  [field.key]: event.target.value,
                })
              }
            />
          )}
          {supportsManagedModels ? (
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <button onClick={() => refreshModels().catch(() => {})} disabled={modelsLoading}>
                {modelsLoading ? "Refreshing..." : "Refresh Models"}
              </button>
              <button
                onClick={() => {
                  const requested = window.prompt("Pull model", selectedModel || "");
                  const modelName = requested?.trim();
                  if (modelName) {
                    pullModel(modelName).catch(() => {});
                  }
                }}
                disabled={modelsLoading}
              >
                Pull Model
              </button>
              <button
                onClick={() => {
                  if (!selectedModel) {
                    return;
                  }
                  if (window.confirm(`Delete model ${selectedModel}?`)) {
                    deleteModel(selectedModel).catch(() => {});
                  }
                }}
                disabled={modelsLoading || !selectedModel}
              >
                Delete Selected
              </button>
            </div>
          ) : (
            <span className="form-hint">Model management is only available for the Ollama provider.</span>
          )}
        </div>
      );
    }

    if (field.inputType === "number") {
      return (
        <div className="hint-row">
          <input
            className="form-input"
            type="number"
            min={field.min ?? undefined}
            max={field.max ?? undefined}
            step={field.step ?? 1}
            value={fieldNumber(field.key)}
            onChange={(event) =>
              setConfigForm({
                ...configForm,
                [field.key]: Number(event.target.value),
              })
            }
          />
          {field.hint && <span className="form-hint">{field.hint}</span>}
        </div>
      );
    }

    return (
      <div className="hint-row">
        <input
          className="form-input"
          value={fieldString(field.key)}
          onChange={(event) =>
            setConfigForm({
              ...configForm,
              [field.key]: event.target.value,
            })
          }
        />
        {field.hint && <span className="form-hint">{field.hint}</span>}
      </div>
    );
  };

  return (
    <>
      {configOpen && configForm && (
        <div
          className="modal-overlay"
          onClick={(event) => {
            if (event.target === event.currentTarget) setConfigOpen(false);
          }}
        >
          <div className="modal-panel">
            <div className="modal-header">
              <span className="modal-title">Provider Configuration</span>
              <button className="modal-close" onClick={() => setConfigOpen(false)}>
                X
              </button>
            </div>
            <div className="modal-body">
              {configError && <p className="form-error">{configError}</p>}
              <div className="form-grid">
                {orderedFields.map((field) => (
                  <div key={field.key} style={{ display: "contents" }}>
                    <label>{field.label}</label>
                    {renderField(field)}
                  </div>
                ))}
              </div>
              <div style={{ marginTop: 12, fontSize: 12, opacity: 0.7 }}>
                Active provider: {currentProvider}
              </div>
            </div>
            <div className="modal-footer">
              <button onClick={() => setConfigOpen(false)}>Cancel</button>
              <button onClick={() => saveConfiguration().catch(() => {})} disabled={configSaving}>
                {configSaving ? "Saving..." : "Save"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}