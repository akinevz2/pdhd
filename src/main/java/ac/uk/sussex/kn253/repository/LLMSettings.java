package ac.uk.sussex.kn253.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * Persisted LLM runtime settings.
 *
 * <p>
 * A single row is created on first use. The {@code load()} helper in
 * {@link ac.uk.sussex.kn253.services.ModelConfigService} ensures the row
 * always exists and defaults are applied.
 *
 * <p>
 * This entity intentionally stores only the minimum state required by this
 * application:
 * base URL, selected model, prompts, and a serialized cache of discovered
 * Ollama models.
 */
@Entity
@Table(name = "ollama_settings")
public class LLMSettings extends PanacheEntityBase {

    public static final String DEFAULT_SYSTEM_PROMPT = "You are PDHD, an AI assistant for software project analysis and filesystem discovery. "
            + "Stay within project-analysis scope; do not answer unrelated general chat from prior knowledge. "
            + "Prefer tool-retrieved evidence over assumptions, and if evidence is missing, state what tool call is needed.";

    public static final String DEFAULT_TOOL_SYSTEM_PROMPT = "Available tool names: listDirectoryContents, change_working_directory, list_files_recursive, analyze_path_detailed, summarize_path, readFile, searchWeb, get_repository_status, get_recent_commits, get_git_branches, get_git_remotes, get_git_diff_stat. "
            + "Use absolute paths within open project roots when possible; if using relative paths, resolve from the current working directory and verify directory context before composing deeper paths. "
            + "Call tools only when needed, with minimum required arguments. "
            + "If a tool result starts with 'Error:' or 'Access denied:', treat it as a failed call and do not use it as evidence. "
            + "Use at most 4 sequential tool calls before reporting findings, blockers, or next required input.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Runtime provider options supported by this application. */
    public enum Provider {
        OLLAMA
    }

    /**
     * Base URL of the provider API, usually {@code http://localhost:11434}.
     */
    @Column(name = "base_url")
    private String baseUrl;

    /** Selected runtime model identifier (e.g. {@code gemma4:8b}). */
    @Column(name = "model_name")
    private String modelName;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "tool_system_prompt", columnDefinition = "TEXT")
    private String toolSystemPrompt;

    /**
     * Serialized JSON array of cached {@code OllamaModelInfo} entries from
     * {@code /api/tags}.
     */
    @Column(name = "ollama_models_json", columnDefinition = "TEXT")
    private String ollamaModelsJson;

    /**
     * Compatibility field for existing database schema.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private Provider provider = Provider.OLLAMA;

    /**
     * Selected runtime embedding model identifier (e.g. {@code qwen3-embedding}).
     * A blank/empty value means embeddings are explicitly disabled at runtime.
     * A {@code null} value means "use the application config default".
     */
    @Column(name = "embedding_model_name")
    private String embeddingModelName;

    public LLMSettings() {
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(final String modelName) {
        this.modelName = modelName;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(final String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getToolSystemPrompt() {
        return toolSystemPrompt;
    }

    public void setToolSystemPrompt(final String toolSystemPrompt) {
        this.toolSystemPrompt = toolSystemPrompt;
    }

    public String getOllamaModelsJson() {
        return ollamaModelsJson;
    }

    public void setOllamaModelsJson(final String ollamaModelsJson) {
        this.ollamaModelsJson = ollamaModelsJson;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(final Provider provider) {
        this.provider = provider;
    }

    public String getEmbeddingModelName() {
        return embeddingModelName;
    }

    public void setEmbeddingModelName(final String embeddingModelName) {
        this.embeddingModelName = embeddingModelName;
    }
}
