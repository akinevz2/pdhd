package ac.uk.sussex.kn253.menu;

import static ac.uk.sussex.kn253.menu.ConfigMenuOption.*;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jline.prompt.*;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.*;

import ac.uk.sussex.kn253.repository.LLMSettings;
import ac.uk.sussex.kn253.repository.OllamaModelInfo;
import ac.uk.sussex.kn253.services.ModelConfigService;
import ac.uk.sussex.kn253.services.OllamaManagementService;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import picocli.CommandLine.Command;

@ApplicationScoped
@Command(name = "configure", description = "Open provider and model configuration menu")
public class OllamaConfigMenu implements Runnable {

    private static final String CONFIRM_TEXT = "Are you sure you want to continue?";

    public static class Status {
        private static final String STATUS_UNCHANGED = "No changes made.";
        private static final String STATUS_PROVIDER_UPDATED = "Provider updated to %s.";
        private static final String STATUS_PROVIDER_UNAVAILABLE = "Provider %s is currently unavailable.";
        private static final String STATUS_BASE_URL_UPDATED = "Base URL updated.";
        private static final String STATUS_BASE_URL_SAVED_WITH_WARNING = "Base URL saved, but connectivity checks failed.";
        private static final String STATUS_BASE_URL_INVALID = "Invalid base URL. Use host:port or http(s)://host:port.";
        private static final String STATUS_BASE_URL_UNRESOLVED = "Could not resolve/reach host:port for %s.";
        private static final String STATUS_BASE_URL_HEALTH_FAILED = "Connection succeeded but Ollama health check failed for %s.";
        private static final String STATUS_MODEL_UPDATED = "Model updated to %s.";
        private static final String STATUS_CACHE_REFRESHED = "Model cache refreshed with %d models.";
        private static final String STATUS_NO_MODELS = "No models available.";
        private static final String STATUS_MODEL_DELETED = "Deleted model %s.";
        private static final String STATUS_MODEL_PULLED = "Pulled model %s.";
        private static final String STATUS_MODEL_PULL_INCOMPLETE = "Pull for model %s finished with status: %s.";
        private static final String STATUS_EMBEDDING_MODEL_UPDATED = "Embedding model updated to %s.";
        private static final String STATUS_EMBEDDING_MODEL_DISABLED = "Embedding model disabled.";
        private static final String STATUS_INVALID_SELECTION = "Invalid selection.";
    }

    @Inject
    @Named("mainTerminal")
    Terminal terminal;

    @Inject
    OllamaManagementService ollamaManagementService;

    @Inject
    ModelConfigService modelConfigService;

    @Override
    public void run() {
        try {
            resolveDependencies();
            menu();
        } catch (final UserInterruptException e) {
            Log.info("Exiting configuration menu...");
        } catch (final Exception e) {
            throw new RuntimeException("Configuration menu failed", e);
        } finally {
            if (terminal != null) {
                terminal.writer().println();
            }
        }
    }

    private void resolveDependencies() {
        if (terminal == null) {
            final Terminal resolvedTerminal = Arc.container().instance(Terminal.class).orElse(null);
            if (resolvedTerminal != null) {
                terminal = resolvedTerminal;
            } else {
                try {
                    terminal = TerminalBuilder.builder().dumb(true).system(true).build();
                } catch (final IOException e) {
                    throw new IllegalStateException("Failed to initialize terminal", e);
                }
            }
        }

        if (ollamaManagementService == null) {
            ollamaManagementService = Arc.container().instance(OllamaManagementService.class).orElse(null);
            if (ollamaManagementService == null) {
                throw new IllegalStateException("OllamaManagementService bean unavailable");
            }
        }

        if (modelConfigService == null) {
            modelConfigService = Arc.container().instance(ModelConfigService.class).orElse(null);
            if (modelConfigService == null) {
                throw new IllegalStateException("ModelConfigService bean unavailable");
            }
        }
    }

    private List<AttributedString> buildMenuHeader() {
        final var settings = modelConfigService.load();
        final String baseUrl = settings.getBaseUrl();
        final String modelName = settings.getModelName();
        final String embeddingModelName = settings.getEmbeddingModelName();

        final boolean urlReachable = baseUrl != null && !baseUrl.isBlank() && isHostPortReachable(baseUrl);
        final Set<String> cachedNames = modelConfigService.getCachedModels().stream()
                .map(OllamaModelInfo::runtimeName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final boolean modelInCache = modelName != null && !modelName.isBlank() && cachedNames.contains(modelName);
        final boolean embeddingInCache = embeddingModelName != null && !embeddingModelName.isBlank()
                && cachedNames.contains(embeddingModelName);

        return List.of(
                new AttributedString("Ollama Configuration Menu"),
                headerEntry("Current base URL", baseUrl, urlReachable),
                headerEntry("Current model", modelName, modelInCache),
                headerEntry("Current embedding model", embeddingModelName, embeddingInCache));
    }

    private AttributedString headerEntry(final String label, final String value, final boolean valid) {
        final String display = value == null || value.isBlank() ? "<unset>" : value.trim();
        return new AttributedStringBuilder()
                .append(label + ": ")
                .style(valid ? AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
                        : AttributedStyle.DEFAULT.faint())
                .append(display)
                .style(AttributedStyle.DEFAULT)
                .toAttributedString();
    }

    private void menu() throws IOException {
        final var prompter = PrompterFactory.create(terminal);
        final Menu menu = new Menu(prompter, this::buildMenuHeader, "Ollama Configuration");

        final Map<MenuOption, MenuCallback> callbacks = new LinkedHashMap<>();
        callbacks.put(REFRESH_MODEL_CACHE, this::refreshModelCache);
        callbacks.put(UPDATE_BASE_URL, () -> updateBaseUrl(prompter));
        callbacks.put(UPDATE_MODEL, () -> updateModel(prompter));
        callbacks.put(UPDATE_EMBEDDING_MODEL, () -> updateEmbeddingModel(prompter));
        callbacks.put(PULL_MODEL, () -> pullModel(prompter));
        callbacks.put(DELETE_MODEL, () -> deleteModel(prompter));
        callbacks.put(UPDATE_PROVIDER, () -> updateProvider(prompter));

        menu.call(callbacks);
    }

    private void deleteModel(final Prompter prompter) throws IOException {
        final var writer = terminal.writer();
        final List<OllamaModelInfo> models = ollamaManagementService.listModels();
        if (models.isEmpty()) {
            writer.println(Status.STATUS_NO_MODELS);
            writer.flush();
            return;
        }

        final Menu menu = new Menu(prompter, "Model Delete Menu", "model to delete");
        final var selectionMap = fromSelection(models.stream().collect(Collectors.toMap(
                ModelMenuSelection::new,
                OllamaModelInfo::getModel)));
        menu.call(confirm(selectionMap, s -> {
            try {
                ollamaManagementService.deleteModel(s);
                writer.println(String.format(Status.STATUS_MODEL_DELETED, s));
            } catch (final RuntimeException ex) {
                Log.errorf(ex, "Failed to delete model %s", s);
                writer.println(ex.getMessage() == null ? "Failed to delete model." : ex.getMessage());
            }
            writer.flush();
        }));
        writer.flush();
    }

    private <A extends MenuOption, S> Map<A, Supplier<S>> fromSelection(final Map<A, S> source) {
        final var map = new LinkedHashMap<A, Supplier<S>>();
        for (final var entry : source.entrySet()) {
            final var v = entry.getValue();
            map.put(entry.getKey(), () -> v);
        }
        return map;
    }

    private <A extends MenuOption> Map<MenuOption, MenuCallback> confirm(final Map<A, Supplier<String>> map,
            final Consumer<String> argumentConsumer)
            throws IOException {
        final var KEY = "confirm";
        final Consumer<MenuCallback> callback = cb -> {
            final var builder = PrompterFactory.create(terminal).newBuilder();
            // bold
            final AttributedStringBuilder asb = new AttributedStringBuilder().style(AttributedStyle.BOLD)
                    .append(CONFIRM_TEXT);
            final var confirmPrompt = builder.createConfirmPrompt()
                    .name(KEY).message(asb.toAnsi())
                    .defaultValue(false)
                    .addPrompt().build();
            final var header = List.of(asb.toAttributedString());
            try {
                final var result = PrompterFactory.create(terminal).prompt(header, confirmPrompt).get(KEY);
                switch (result.getResult()) {
                    case "YES" -> {
                        cb.call();
                    }
                }
            } catch (final UserInterruptException e) {
            } catch (final IOException e) {
                Log.warn(e);
            }
            Log.info("Edit Cancelled");
        };
        final Map<MenuOption, MenuCallback> resultMap = new HashMap<>();
        for (final var entry : map.entrySet()) {
            final MenuOption menuOption = entry.getKey();
            final Supplier<String> modelSupplier = entry.getValue();
            resultMap.put(menuOption, () -> callback.accept(() -> argumentConsumer.accept(modelSupplier.get())));
        }
        return resultMap;
    }

    private void pullModel(final Prompter prompter) throws IOException {
        final var writer = terminal.writer();
        final var builder = prompter.newBuilder();
        builder.createInputPrompt()
                .name("pull-model-name")
                .message("Model name to pull:")
                .addPrompt();
        final var result = prompter
                .prompt(List.of(new AttributedString("Pull Model")), builder.build())
                .get("pull-model-name");
        if (!(result instanceof final InputResult inputResult)) {
            return;
        }
        final String modelName = inputResult.getInput();
        if (modelName == null || modelName.isBlank()) {
            writer.println(Status.STATUS_INVALID_SELECTION);
            writer.flush();
            return;
        }
        final String trimmed = modelName.trim();
        final var settings = modelConfigService.load();
        final String baseUrl = settings.getBaseUrl();
        writer.println("Starting pull: " + trimmed);
        writer.flush();
        try {
            if (!checkConnectivity(baseUrl, writer)) {
                writer.flush();
                return;
            }
            final int width = terminal.getWidth();
            final var finalStatus = ollamaManagementService.pullModelStreaming(baseUrl, trimmed, event -> {
                final var bar = new OllamaPullStatusBar(
                        trimmed, event.getStatus(), event.getCompleted(), event.getTotal(), width);
                writer.print("\r\u001B[2K" + bar.render().get(1).toAnsi());
                writer.flush();
            });
            writer.println();
            if (finalStatus.isSuccess()) {
                writer.println(String.format(Status.STATUS_MODEL_PULLED, trimmed));
            } else {
                writer.println(String.format(Status.STATUS_MODEL_PULL_INCOMPLETE, trimmed, finalStatus.getStatus()));
            }
        } catch (final RuntimeException ex) {
            Log.errorf(ex, "Failed to pull model %s", trimmed);
            writer.println(ex.getMessage() == null ? "Failed to pull model." : ex.getMessage());
        }
        writer.flush();
    }

    private void updateEmbeddingModel(final Prompter prompter) throws IOException {
        final var writer = terminal.writer();
        final String toggleSelection = "TOGGLE_EMBEDDING";
        final var currentSettings = modelConfigService.load();
        final boolean embeddingsDisabled = currentSettings.getEmbeddingModelName() == null
                || currentSettings.getEmbeddingModelName().isBlank();

        final Map<ModelMenuSelection, String> selectionMap = new LinkedHashMap<>();
        selectionMap.put(
                new ModelMenuSelection(
                        embeddingsDisabled ? "Enable embeddings" : "Disable embeddings",
                        toggleSelection),
                toggleSelection);
        for (final var model : modelConfigService.getCachedModels()) {
            final String name = model.runtimeName();
            if (name != null && !name.isBlank()) {
                selectionMap.put(new ModelMenuSelection(name, name), name);
            }
        }
        final var menu = new Menu(prompter, "Update Embedding Model", "embedding model");
        menu.call(confirm(fromSelection(selectionMap), embeddingModel -> {
            final var settings = modelConfigService.load();

            if (toggleSelection.equals(embeddingModel)) {
                final String currentEmbeddingModel = settings.getEmbeddingModelName();
                if (currentEmbeddingModel == null || currentEmbeddingModel.isBlank()) {
                    final String fallbackEmbeddingModel = modelConfigService.getCachedModels().stream()
                            .map(OllamaModelInfo::runtimeName)
                            .filter(Objects::nonNull)
                            .filter(name -> !name.isBlank())
                            .filter(name -> name.toLowerCase(Locale.ROOT).contains("embed"))
                            .findFirst()
                            .orElseGet(() -> modelConfigService.getCachedModels().stream()
                                    .map(OllamaModelInfo::runtimeName)
                                    .filter(Objects::nonNull)
                                    .filter(name -> !name.isBlank())
                                    .findFirst()
                                    .orElse(null));

                    if (fallbackEmbeddingModel == null || fallbackEmbeddingModel.isBlank()) {
                        writer.println(Status.STATUS_NO_MODELS);
                        writer.flush();
                        return;
                    }

                    settings.setEmbeddingModelName(fallbackEmbeddingModel);
                    modelConfigService.save(settings);
                    writer.println(String.format(Status.STATUS_EMBEDDING_MODEL_UPDATED, fallbackEmbeddingModel));
                    writer.flush();
                    return;
                }

                settings.setEmbeddingModelName("");
                modelConfigService.save(settings);
                writer.println(Status.STATUS_EMBEDDING_MODEL_DISABLED);
                writer.flush();
                return;
            }

            settings.setEmbeddingModelName(embeddingModel);
            modelConfigService.save(settings);
            if (embeddingModel.isBlank()) {
                writer.println(Status.STATUS_EMBEDDING_MODEL_DISABLED);
            } else {
                writer.println(String.format(Status.STATUS_EMBEDDING_MODEL_UPDATED, embeddingModel));
            }
            writer.flush();
        }));
    }

    private void refreshModelCache() {
        final var writer = terminal.writer();
        final var settings = modelConfigService.load();
        final String baseUrl = settings.getBaseUrl();
        if (!checkConnectivity(baseUrl, writer)) {
            writer.println(Status.STATUS_BASE_URL_UNRESOLVED);
            writer.flush();
            return;
        }
        final var models = modelConfigService.refreshModelCache();
        if (models.isEmpty()) {
            writer.println(Status.STATUS_NO_MODELS);
        } else {
            writer.println(String.format(Status.STATUS_CACHE_REFRESHED, models.size()));
        }
        writer.flush();
    }

    private void updateBaseUrl(final Prompter prompter) throws IOException {
        final var writer = terminal.writer();
        final var builder = prompter.newBuilder();
        builder.createInputPrompt()
                .name("base-url")
                .message("Ollama base URL:")
                .addPrompt();
        final var result = prompter
                .prompt(List.of(new AttributedString("Update Base URL")), builder.build())
                .get("base-url");
        if (!(result instanceof final InputResult ir)) {
            return;
        }
        final String url = ir.getInput();
        if (url == null || url.isBlank()) {
            writer.println(Status.STATUS_UNCHANGED);
            writer.flush();
            return;
        }

        final String normalized;
        try {
            normalized = normalizeBaseUrl(url);
        } catch (final IllegalArgumentException ex) {
            writer.println(Status.STATUS_BASE_URL_INVALID);
            writer.flush();
            return;
        }

        final var settings = modelConfigService.load();
        final String previousBaseUrl = settings.getBaseUrl();
        if (normalized.equals(previousBaseUrl)) {
            writer.println(Status.STATUS_UNCHANGED);
            writer.flush();
            return;
        }

        settings.setBaseUrl(normalized);
        modelConfigService.save(settings);

        if (!isHostPortReachable(normalized)) {
            writer.println(Status.STATUS_BASE_URL_UPDATED);
            writer.println(String.format(Status.STATUS_BASE_URL_UNRESOLVED, normalized));
            writer.println(Status.STATUS_BASE_URL_SAVED_WITH_WARNING);
            writer.flush();
            return;
        }

        if (!ollamaManagementService.isHealthy(normalized)) {
            writer.println(Status.STATUS_BASE_URL_UPDATED);
            writer.println(String.format(Status.STATUS_BASE_URL_HEALTH_FAILED, normalized));
            writer.println(Status.STATUS_BASE_URL_SAVED_WITH_WARNING);
            writer.flush();
            return;
        }

        writer.println(Status.STATUS_BASE_URL_UPDATED);
        writer.flush();
    }

    /**
     * Checks that {@code baseUrl} is reachable and that the Ollama health endpoint
     * responds.
     * Prints diagnostic messages to {@code writer} and returns {@code false} if
     * either check fails.
     */
    private boolean checkConnectivity(final String baseUrl, final java.io.PrintWriter writer) {
        if (baseUrl == null || baseUrl.isBlank()) {
            writer.println(Status.STATUS_BASE_URL_INVALID);
            return false;
        }
        if (!isHostPortReachable(baseUrl)) {
            writer.println(String.format(Status.STATUS_BASE_URL_UNRESOLVED, baseUrl));
            return false;
        }
        if (!ollamaManagementService.isHealthy(baseUrl)) {
            writer.println(String.format(Status.STATUS_BASE_URL_HEALTH_FAILED, baseUrl));
            return false;
        }
        return true;
    }

    private String normalizeBaseUrl(final String rawInput) {
        final String trimmed = rawInput == null ? "" : rawInput.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("blank url");
        }

        final String candidate = (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
                ? trimmed
                : "http://" + trimmed;

        final URI uri;
        try {
            uri = new URI(candidate);
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException("invalid uri", ex);
        }

        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("missing host");
        }

        final String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("unsupported scheme");
        }

        final int port = uri.getPort() > 0 ? uri.getPort() : ("https".equals(scheme) ? 443 : 11434);
        return scheme + "://" + host + ":" + port;
    }

    private boolean isHostPortReachable(final String baseUrl) {
        try {
            final URI uri = new URI(baseUrl);
            final String host = uri.getHost();
            final int port = uri.getPort();
            if (host == null || host.isBlank() || port <= 0) {
                return false;
            }
            final InetAddress address = InetAddress.getByName(host);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, port), 2000);
            }
            return true;
        } catch (final UnknownHostException ex) {
            return false;
        } catch (final Exception ex) {
            return false;
        }
    }

    private void updateModel(final Prompter prompter) throws IOException {
        final var writer = terminal.writer();
        final Map<ModelMenuSelection, String> selectionMap = new LinkedHashMap<>();
        for (final var model : modelConfigService.getCachedModels()) {
            final String name = model.runtimeName();
            if (name != null && !name.isBlank()) {
                selectionMap.put(new ModelMenuSelection(name, name), name);
            }
        }
        if (selectionMap.isEmpty()) {
            writer.println(Status.STATUS_NO_MODELS);
            writer.flush();
            return;
        }
        final var menu = new Menu(prompter, "Update Model", "model");
        menu.call(confirm(fromSelection(selectionMap), modelName -> {
            final var settings = modelConfigService.load();
            settings.setModelName(modelName);
            modelConfigService.save(settings);
            writer.println(String.format(Status.STATUS_MODEL_UPDATED, modelName));
            writer.flush();
        }));
    }

    private void updateProvider(final Prompter prompter) throws IOException {
        final var writer = terminal.writer();
        final var builder = prompter.newBuilder();
        final var listPrompt = builder.createListPrompt()
                .name("provider-select")
                .message("Select provider:");
        listPrompt.newItem(LLMSettings.Provider.OLLAMA.name()).text(LLMSettings.Provider.OLLAMA.name()).add();

        final String disabledOpenAi = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.faint())
                .append(LLMSettings.Provider.OPENAI.name() + " (disabled)")
                .style(AttributedStyle.DEFAULT)
                .toAnsi();
        listPrompt.newItem("OPENAI_DISABLED").text(disabledOpenAi).add();

        listPrompt.addPrompt();
        final var result = prompter
                .prompt(List.of(new AttributedString("Update Provider")), builder.build())
                .get("provider-select");
        if (!(result instanceof final ListResult lr)) {
            return;
        }
        if ("OPENAI_DISABLED".equals(lr.getSelectedId())) {
            writer.println(String.format(Status.STATUS_PROVIDER_UNAVAILABLE, LLMSettings.Provider.OPENAI.name()));
            writer.flush();
            return;
        }
        try {
            final var provider = LLMSettings.Provider.valueOf(lr.getSelectedId());
            final var settings = modelConfigService.load();
            final String baseUrl = settings.getBaseUrl();
            if (!checkConnectivity(baseUrl, writer)) {
                writer.flush();
                return;
            }
            settings.setProvider(provider);
            modelConfigService.save(settings);
            writer.println(String.format(Status.STATUS_PROVIDER_UPDATED, provider));
        } catch (final IllegalArgumentException e) {
            writer.println(Status.STATUS_INVALID_SELECTION);
        }
        writer.flush();
    }

}
