package ac.uk.sussex.kn253.ollama;

import java.util.Optional;

import io.smallrye.config.*;

/**
 * Typed configuration for the Ollama integration.
 *
 * <p>
 * Values are read from {@code application.properties} (or any MicroProfile
 * Config source)
 * under the {@code ollama} prefix. Override them at runtime via environment
 * variables using
 * the standard MicroProfile naming convention, e.g.:
 * 
 * <pre>
 *   OLLAMA_BASE_URL=http://my-gpu-box:11434
 *   OLLAMA_MODEL_NAME=gemma4
 * </pre>
 */
@ConfigMapping(prefix = "pdhd.ollama")
public interface OllamaConfig {

    @WithName("enabled")
    @WithDefault("true")
    boolean enabled();

    /** Base URL of the Ollama HTTP API, e.g. {@code http://localhost:11434}. */
    @WithName("base-url")
    Optional<String> baseUrl();

    /** Default model name used when no explicit model is supplied. */
    @WithName("model-name")
    @WithDefault("gemma4")
    String modelName();

    /** Default embeddings model name used when no explicit model is supplied. */
    @WithName("embedding-model-name")
    @WithDefault("qwen3-embedding")
    String embeddingModelName();

    /** Request timeout in seconds for chat / generate calls. */
    @WithName("timeout-seconds")
    @WithDefault("120")
    int timeoutSeconds();

    /** Temperature (0.0 – 2.0). Higher = more creative. */
    @WithName("temperature")
    @WithDefault("0.7")
    double temperature();

    /** Maximum number of tokens to generate. -1 means model default. */
    @WithName("num-predict")
    @WithDefault("-1")
    int numPredict();

    /** Context window size in tokens. 0 means model default. */
    @WithName("num-ctx")
    @WithDefault("0")
    int numCtx();

    /** Whether embeddings are enabled. */
    @WithName("embedding-enabled")
    @WithDefault("true")
    Boolean embeddingEnabled();

    @WithName("embedding-max-results")
    @WithDefault("5")
    Integer embeddingMaxResults();

    @WithName("embedding-dimension")
    @WithDefault("384")
    Integer embeddingDimension();

}
