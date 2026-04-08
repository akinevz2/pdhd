package ac.uk.sussex.kn253.services;

import java.util.Locale;
import java.util.logging.Logger;

import ac.uk.sussex.kn253.repository.LLMSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OllamaStartupCoordinator {

    private static final Logger LOG = Logger.getLogger(OllamaStartupCoordinator.class.getName());
    private static final String CONFIGURE_COMMAND = "configure";

    @Inject
    ModelConfigService modelConfigService;

    @Inject
    OllamaManagementService ollamaManagementService;

    @Inject
    OllamaRuntimeEndpointService runtimeEndpointService;

    @Inject
    OllamaTestcontainersService ollamaTestcontainersService;

    public void prepare(final String commandName) {
        final String normalizedCommand = normalizeCommand(commandName);
        final String persistedBaseUrl = loadPersistedBaseUrl();
        final String preferredBaseUrl = runtimeEndpointService.resolvePersistedOrActive(persistedBaseUrl);

        runtimeEndpointService.setRuntimeBaseUrl(preferredBaseUrl);
        if (ollamaManagementService.isHealthy(preferredBaseUrl)) {
            LOG.info(() -> String.format("Ollama reachable at startup: %s", preferredBaseUrl));
            return;
        }

        LOG.warning(() -> String.format("Ollama unreachable at startup: %s", preferredBaseUrl));

        try {
            final String containerBaseUrl = ollamaTestcontainersService.startAndGetEndpoint();
            if (!ollamaManagementService.isHealthy(containerBaseUrl)) {
                throw new IllegalStateException("Testcontainers Ollama started but is not reachable");
            }
            runtimeEndpointService.setRuntimeBaseUrl(containerBaseUrl);
            ollamaManagementService.warmUpClient(containerBaseUrl);
        } catch (final RuntimeException e) {
            if (CONFIGURE_COMMAND.equals(normalizedCommand)) {
                LOG.severe(() -> String.format(
                        "Testcontainers fallback failed, continuing for '%s' command so configuration can proceed: %s",
                        CONFIGURE_COMMAND,
                        e.getMessage()));
                return;
            }
            throw new IllegalStateException("Unable to reach configured Ollama and Testcontainers fallback failed", e);
        }
    }

    private String loadPersistedBaseUrl() {
        try {
            final LLMSettings settings = modelConfigService.load();
            if (settings == null || settings.getBaseUrl() == null || settings.getBaseUrl().isBlank()) {
                return null;
            }
            return settings.getBaseUrl();
        } catch (final RuntimeException e) {
            LOG.warning(() -> "Could not load persisted Ollama settings at startup: " + e.getMessage());
            return null;
        }
    }

    private String normalizeCommand(final String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return "webui";
        }
        return commandName.trim().toLowerCase(Locale.ROOT);
    }
}