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

    public static final String DEFAULT_SYSTEM_PROMPT = "You are PDHD, an AI assistant specialising in software project analysis and discovery. "
            + "You are helpful, focused and technically precise.";

    public static final String DEFAULT_TOOL_SYSTEM_PROMPT = "When using tools, be precise and efficient. "
            + "Only call tools when necessary to answer the user's question.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Preserved for backward-compatible schema inserts. */
    public enum Provider {
        OLLAMA,
        OPENAI
    }

    /**
     * Base URL of the provider API, usually {@code http://localhost:11434}.
     */
    @Column(name = "base_url")
    private String baseUrl;

    /** Selected runtime model identifier (e.g. {@code llama3.1:8b}). */
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
