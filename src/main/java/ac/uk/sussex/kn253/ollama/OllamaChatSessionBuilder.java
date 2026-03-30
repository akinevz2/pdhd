package ac.uk.sussex.kn253.ollama;

import ac.uk.sussex.kn253.services.ToolActivityService;
import ac.uk.sussex.kn253.services.ToolService;

public class OllamaChatSessionBuilder implements OllamaConfig {

    // NB: this builder is always instantiated via `new` (not CDI) – never use
    // @Inject here; field defaults must be plain literals.

    private String baseUrl;
    private String modelName;
    private String embeddingModelName = "qwen3-embedding";
    private int timeoutSeconds = 120;
    private double temperature = 0.7;
    private int numPredict = -1;
    private int numCtx = 0;

    private ToolService toolService;
    private ToolActivityService toolActivityService;

    private boolean embeddingEnabled = true;
    private int embeddingMaxResults = 5;
    private int embeddingDimension = 384;

    public OllamaChatSessionBuilder baseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public OllamaChatSessionBuilder model(final String modelName) {
        this.modelName = modelName;
        return this;
    }

    public OllamaChatSessionBuilder embeddingModel(final String embeddingModelName) {
        this.embeddingModelName = embeddingModelName;
        return this;
    }

    public OllamaChatSessionBuilder timeoutSeconds(final int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public OllamaChatSessionBuilder temperature(final double temperature) {
        this.temperature = temperature;
        return this;
    }

    public OllamaChatSessionBuilder numPredict(final int numPredict) {
        this.numPredict = numPredict;
        return this;
    }

    public OllamaChatSessionBuilder numCtx(final int numCtx) {
        this.numCtx = numCtx;
        return this;
    }

    public OllamaChatSession build() {
        return new OllamaChatSession(this);
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public String embeddingModelName() {
        return embeddingModelName;
    }

    @Override
    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public double temperature() {
        return temperature;
    }

    @Override
    public int numPredict() {
        return numPredict;
    }

    @Override
    public int numCtx() {
        return numCtx;
    }

    public OllamaChatSessionBuilder toolService(final ToolService toolService) {
        this.toolService = toolService;
        return this;
    }

    public ToolService toolService() {
        return toolService;
    }

    public OllamaChatSessionBuilder toolActivityService(final ToolActivityService toolActivityService) {
        this.toolActivityService = toolActivityService;
        return this;
    }

    public ToolActivityService toolActivityService() {
        return toolActivityService;
    }

    public OllamaChatSessionBuilder embeddingEnabled(final boolean embeddingEnabled) {
        this.embeddingEnabled = embeddingEnabled;
        return this;
    }

    public OllamaChatSessionBuilder embeddingMaxResults(final int embeddingMaxResults) {
        this.embeddingMaxResults = embeddingMaxResults;
        return this;
    }

    public OllamaChatSessionBuilder embeddingDimension(final int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
        return this;
    }

    @Override
    public Boolean embeddingEnabled() {
        return embeddingEnabled;
    }

    @Override
    public Integer embeddingMaxResults() {
        return embeddingMaxResults;
    }

    @Override
    public Integer embeddingDimension() {
        return embeddingDimension;
    }

}
