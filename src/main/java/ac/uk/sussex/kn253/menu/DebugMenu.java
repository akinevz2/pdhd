package ac.uk.sussex.kn253.menu;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.jline.prompt.*;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;

import ac.uk.sussex.kn253.repository.*;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;
import picocli.CommandLine.Command;

@ApplicationScoped
@Command(name = "debug", description = "Open debugging menu")
public class DebugMenu implements Runnable {

    public static class Status {
        private static final String STATUS_NO_TELEMETRY = "No telemetry records found.";
        private static final String STATUS_NO_KNOWLEDGE = "No project knowledge entries found.";
        private static final String STATUS_KNOWLEDGE_RESET_WARNING = "This will delete ALL rows from project_knowledge.";
        private static final String STATUS_KNOWLEDGE_RESET_ABORTED = "Reset aborted: confirmation mismatch.";
        private static final String STATUS_UNKNOWN_SELECTION = "Unknown debug action selected: %s";
    }

    public enum MenuOption {
        EXIT("Exit menu"),
        TELEMETRY("List telemetry records"),
        KNOWLEDGE("List project knowledge"),
        RESET("Reset knowledge base"),
        STATUS("Show RAG status");

        private final String label;

        private MenuOption(final String labelString) {
            this.label = labelString;
        }

        public String getLabel() {
            return label;
        }
    }

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Inject
    @Named("mainTerminal")
    Terminal terminal;

    @Override
    public void run() {
        try {
            resolveDependencies();
            menu();
        } catch (final UserInterruptException e) {
            Log.info("Exiting debug menu...");
        } catch (final Exception e) {
            throw new RuntimeException("Debug menu failed", e);
        }
    }

    private void resolveDependencies() {
        if (terminal != null) {
            return;
        }
        final Terminal resolved = Arc.container().instance(Terminal.class).orElse(null);
        if (resolved != null) {
            terminal = resolved;
            return;
        }
        try {
            terminal = TerminalBuilder.builder().dumb(true).system(true).build();
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to initialize terminal", e);
        }
    }

    private void menu() throws IOException {
        final Prompter prompter = PrompterFactory.create(terminal);
        final PromptBuilder builder = prompter.newBuilder();

        final List<AttributedString> header = List.of(
                new AttributedString("Debug Menu"));

        final ListBuilder listPrompt = builder.createListPrompt()
                .name("debug-menu")
                .message("Choose debug action:");

        for (final var option : MenuOption.values()) {
            listPrompt
                    .newItem(option.name())
                    .text(option.getLabel())
                    .add();
        }

        listPrompt.addPrompt();

        final PromptResult<? extends Prompt> result = prompter
                .prompt(header, builder.build())
                .get("debug-menu");
        if (!(result instanceof final ListResult listResult)) {
            return;
        }

        final var writer = terminal.writer();
        final String selectedId = listResult.getSelectedId();
        Log.infof("Selected debug action: %s", selectedId);
        switch (selectedId) {
            case "TELEMETRY" -> listTelemetry(writer);
            case "KNOWLEDGE" -> listKnowledge(writer);
            case "RESET" -> resetKnowledgeBase(prompter, writer);
            case "STATUS" -> showRagStatus(writer);
            case "EXIT" -> Log.info("Exiting debug menu...");
            default -> Log.warnf(Status.STATUS_UNKNOWN_SELECTION, selectedId);
        }
        writer.flush();
    }

    @Transactional
    void listTelemetry(final java.io.PrintWriter writer) {
        final List<ToolTelemetryRecord> records = ToolTelemetryRecord
                .find("ORDER BY recordedAt DESC")
                .list();
        final List<ModelCallTelemetryRecord> modelRecords = ModelCallTelemetryRecord
                .find("ORDER BY recordedAt DESC")
                .list();
        if (records.isEmpty() && modelRecords.isEmpty()) {
            writer.println(Status.STATUS_NO_TELEMETRY);
            return;
        }

        writer.println("Tool telemetry records (newest first):");
        if (records.isEmpty()) {
            writer.println("- <none>");
        }
        for (final ToolTelemetryRecord record : records) {
            writer.printf(
                    "- id=%d time=%s tool=%s module=%s success=%s durationMs=%.3f error=%s argValidationFailure=%s%n",
                    record.id,
                    TS_FORMAT.format(java.time.Instant.ofEpochMilli(record.recordedAt)),
                    safe(record.toolName),
                    safe(record.moduleName),
                    record.success,
                    record.durationNanos / 1_000_000.0,
                    safe(record.errorClass),
                    record.argumentValidationFailure);
        }

        writer.println("Model call telemetry records (newest first):");
        if (modelRecords.isEmpty()) {
            writer.println("- <none>");
        }
        for (final ModelCallTelemetryRecord record : modelRecords) {
            writer.printf(
                    "- id=%d time=%s model=%s success=%s durationMs=%.3f requestTokens=%d responseTokens=%d cwd=%s error=%s%n",
                    record.id,
                    TS_FORMAT.format(java.time.Instant.ofEpochMilli(record.recordedAt)),
                    safe(record.modelName),
                    record.success,
                    record.durationNanos / 1_000_000.0,
                    record.requestTokenCount,
                    record.responseTokenCount,
                    safe(record.currentWorkingDirectory),
                    safe(record.errorClass));
        }
    }

    @Transactional
    void listKnowledge(final java.io.PrintWriter writer) {
        final List<ProjectKnowledge> entries = ProjectKnowledge
                .find("SELECT pk FROM ProjectKnowledge pk LEFT JOIN FETCH pk.project ORDER BY pk.updatedAt DESC")
                .list();
        if (entries.isEmpty()) {
            writer.println(Status.STATUS_NO_KNOWLEDGE);
            return;
        }

        writer.println("Project knowledge entries (newest first):");
        for (final ProjectKnowledge entry : entries) {
            final String projectDir = entry.getProject() == null ? "<none>" : safe(entry.getProject().getDirectory());
            final int contentLength = entry.getJsonContent() == null ? 0 : entry.getJsonContent().length();
            final boolean hasEmbedding = entry.getEmbeddingVector() != null && !entry.getEmbeddingVector().isBlank();
            writer.printf("- id=%d project=%s key=%s updated=%s contentLen=%d embedded=%s%n",
                    entry.id,
                    projectDir,
                    safe(entry.getKey()),
                    entry.getUpdatedAt(),
                    contentLength,
                    hasEmbedding);
        }
    }

    void resetKnowledgeBase(final Prompter prompter, final java.io.PrintWriter writer) throws IOException {
        writer.println(Status.STATUS_KNOWLEDGE_RESET_WARNING);
        writer.flush();
        final var builder = prompter.newBuilder();
        builder.createInputPrompt()
                .name("confirm")
                .message("Type RESET to confirm:")
                .addPrompt();
        final var result = (InputResult) prompter.prompt(List.of(), builder.build()).get("confirm");
        if (result == null || !"RESET".equals(result.getInput())) {
            writer.println(Status.STATUS_KNOWLEDGE_RESET_ABORTED);
            return;
        }
        // final long deleted = ragService.clearKnowledgeBase();
        // writer.println("Knowledge base reset complete. Deleted rows: " + deleted);
        throw new UnsupportedOperationException("Knowledge base reset not implemented yet");
    }

    void showRagStatus(final java.io.PrintWriter writer) {
        // writer.println("RAG available: " + ragService.isAvailable());
        // writer.println("Indexed entries: " + ragService.getIndexedCount());
        throw new UnsupportedOperationException("RAG status not implemented yet");
    }

    private static String safe(final String value) {
        return (value == null || value.isBlank()) ? "<none>" : value;
    }

}
