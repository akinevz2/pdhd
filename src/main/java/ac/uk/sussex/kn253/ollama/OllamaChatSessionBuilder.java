package ac.uk.sussex.kn253.ollama;

import ac.uk.sussex.kn253.services.ToolService;

public class OllamaChatSessionBuilder implements OllamaConfig {

    private String baseUrl;
    private String modelName;
    private int timeoutSeconds = 120;
    private boolean streaming = false;
    private double temperature = 0.7;
    private int numPredict = -1;
    private int numCtx = 0;
    private ToolService toolService;

    public OllamaChatSessionBuilder baseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public OllamaChatSessionBuilder model(final String modelName) {
        this.modelName = modelName;
        return this;
    }

    public OllamaChatSessionBuilder timeoutSeconds(final int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public OllamaChatSessionBuilder streaming(final boolean streaming) {
        this.streaming = streaming;
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
    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public boolean streaming() {
        return streaming;
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

}
