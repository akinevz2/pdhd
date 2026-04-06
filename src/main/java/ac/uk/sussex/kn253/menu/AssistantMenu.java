package ac.uk.sussex.kn253.menu;

import java.nio.file.Path;

import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;

import ac.uk.sussex.kn253.events.ChatRepaintEvent;
import ac.uk.sussex.kn253.events.ChatResizedEvent;
import ac.uk.sussex.kn253.services.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import picocli.CommandLine.Command;

@ApplicationScoped
@Command(name = "assistant", description = "Launch assistant")
public class AssistantMenu implements Runnable {

    private static final String ASSISTANT_ROLE_FALLBACK = AssistantService.DEFAULT_PROMPT_PREFIX;

    private final Terminal terminal;
    private final AssistantService assistantService;
    private final ModelConfigService modelConfigService;
    private final AssistantWorkingDirectoryService assistantWorkingDirectoryService;
    private final RequestContextController requestContextController;
    private final TelemetryService telemetryService;
    private final Event<ChatResizedEvent> chatResizedEvents;
    private final Event<ChatRepaintEvent> chatRepaintEvents;

    private volatile AssistantTerminalAdapter activeTerminalAdapter;

    @Inject
    AssistantMenu(
            @Named("mainTerminal") final Terminal terminal,
            final AssistantService assistantService,
            final ModelConfigService modelConfigService,
            final AssistantWorkingDirectoryService assistantWorkingDirectoryService,
            final RequestContextController requestContextController,
            final TelemetryService telemetryService,
            final Event<ChatResizedEvent> chatResizedEvents,
            final Event<ChatRepaintEvent> chatRepaintEvents) {
        this.terminal = terminal;
        this.assistantService = assistantService;
        this.modelConfigService = modelConfigService;
        this.assistantWorkingDirectoryService = assistantWorkingDirectoryService;
        this.requestContextController = requestContextController;
        this.telemetryService = telemetryService;
        this.chatResizedEvents = chatResizedEvents;
        this.chatRepaintEvents = chatRepaintEvents;
    }

    @Override
    public void run() {
        final boolean activated = requestContextController.activate();
        try {
            final var terminalAdapter = new AssistantTerminalAdapter(terminal);
            final var lineReader = LineReaderBuilder.builder().terminal(terminalAdapter).build();
            final String modelName = resolveModelName();
            final var inputAdapter = new AssistantInputAdapter(
                    lineReader,
                    () -> formatUserPrompt(assistantWorkingDirectoryService.getCurrentWorkingDirectory()),
                    () -> terminalAdapter.scrollUp(2),
                    () -> terminalAdapter.scrollDown(2),
                    event -> chatResizedEvents.fire(event));
            final var controller = new AssistantSessionController(
                    inputAdapter,
                    terminalAdapter,
                    assistantService,
                    telemetryService,
                    assistantWorkingDirectoryService,
                    modelName,
                    resolveAssistantRole(modelName),
                    this::estimateTokens);
            activeTerminalAdapter = terminalAdapter;
            try (inputAdapter; terminalAdapter) {
                controller.run();
            } finally {
                activeTerminalAdapter = null;
            }
        } finally {
            if (activated) {
                requestContextController.deactivate();
            }
        }
    }

    void onChatResized(final @Observes ChatResizedEvent event) {
        chatRepaintEvents.fire(new ChatRepaintEvent(
                "terminal-resized:" + event.previousWidth() + "x" + event.previousHeight() + "->"
                        + event.width() + "x" + event.height()));
    }

    void onChatRepaint(final @Observes ChatRepaintEvent event) {
        if (activeTerminalAdapter != null) {
            activeTerminalAdapter.render();
        }
    }

    private String resolveModelName() {
        final var settings = modelConfigService.load();
        if (settings == null || settings.getModelName() == null || settings.getModelName().isBlank()) {
            return "assistant";
        }
        return settings.getModelName().trim();
    }

    private String resolveAssistantRole(final String modelName) {
        if (modelName == null || modelName.isBlank() || "assistant".equals(modelName)) {
            return ASSISTANT_ROLE_FALLBACK;
        }
        return "assistant(" + modelName + ")> ";
    }

    private int estimateTokens(final String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    static String formatUserPrompt(final String currentWorkingDirectory) {
        return shortenPath(currentWorkingDirectory) + " > ";
    }

    static String shortenPath(final String path) {
        if (path == null || path.isBlank()) {
            return "?";
        }

        try {
            final Path normalizedPath = Path.of(path).normalize();
            final int nameCount = normalizedPath.getNameCount();
            if (nameCount == 0) {
                final Path rootOnly = normalizedPath.getRoot();
                return rootOnly == null ? "/" : rootOnly.toString();
            }

            final StringBuilder result = new StringBuilder();
            final Path root = normalizedPath.getRoot();
            if (root != null) {
                result.append(root.toString());
            }

            for (int i = 0; i < nameCount; i++) {
                if (result.length() > 0 && result.charAt(result.length() - 1) != '/') {
                    result.append('/');
                }
                final String segment = normalizedPath.getName(i).toString();
                final String value = i == nameCount - 1 ? segment : segment.substring(0, 1);
                result.append(value);
            }

            return result.toString();
        } catch (final RuntimeException ex) {
            return path;
        }
    }

}