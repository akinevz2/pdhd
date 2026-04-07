package ac.uk.sussex.kn253.commands;

import java.util.logging.Logger;

import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.services.ModelConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@ApplicationScoped
@Command(name = "configure", description = "Update model configuration (non-interactive)")
public class OllamaConfigCommand implements Runnable {

    private static final Logger LOG = Logger.getLogger(OllamaConfigCommand.class.getName());

    private final ModelConfigService modelConfigService;

    @Option(names = "--base-url", description = "Set Ollama base URL")
    String baseUrl;

    @Option(names = "--model", description = "Set chat model name")
    String modelName;

    @Option(names = "--embedding-model", description = "Set embedding model name")
    String embeddingModelName;

    @Option(names = "--refresh-model-cache", description = "Refresh cached Ollama model list")
    boolean refreshModelCache;

    @Inject
    OllamaConfigCommand(final ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @Override
    public void run() {
        final LLMSettings settings = modelConfigService.load();
        boolean changed = false;

        if (baseUrl != null && !baseUrl.isBlank()) {
            settings.setBaseUrl(baseUrl.trim());
            changed = true;
        }
        if (modelName != null && !modelName.isBlank()) {
            settings.setModelName(modelName.trim());
            changed = true;
        }
        if (embeddingModelName != null) {
            settings.setEmbeddingModelName(embeddingModelName.trim());
            changed = true;
        }

        if (changed) {
            modelConfigService.save(settings);
            LOG.info("Configuration updated");
        }

        if (refreshModelCache) {
            final int count = modelConfigService.refreshModelCache().size();
            LOG.info(() -> "Model cache refreshed: " + count + " model(s)");
        }
    }
}
