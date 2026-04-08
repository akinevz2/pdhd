package ac.uk.sussex.kn253.tools;

import java.nio.file.Path;
import java.util.List;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.repository.ProjectKnowledge;
import ac.uk.sussex.kn253.services.*;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Tooling for project summary artifact recall.
 */
@ApplicationScoped
public class ProjectSummaryTools {

    public static final String PROJECT_SUMMARY_KEY = "project-summary:latest";
    public static final String PROJECT_NEXT_STEPS_KEY = "project-next-steps:latest";
    public static final String FOLDER_SUMMARY_PREFIX = "folder-summary:";

    @Inject
    CwdService cwdService;

    @Inject
    ProjectKnowledgeSummaryStoreService store;

    @Inject
    TelemetryService telemetryService;

    @Inject
    EmbeddingRetrievalService embeddingRetrievalService;

    @Inject
    GraphRagPipelineService graphRagPipelineService;

    @Tool(name = "read_project_summary_report", value = {
            "Read the latest persisted project summary report for a project path."
    })
    public String readProjectSummaryReport(final String projectPath) {
        return withTelemetry("read_project_summary_report", projectPath, () -> {
            final ProjectFolder project = requireProject(projectPath);
            final String report = store.read(project, PROJECT_SUMMARY_KEY);
            return report == null || report.isBlank()
                    ? "No project summary report is available yet for: " + project.getDirectory()
                    : report;
        });
    }

    @Tool(name = "read_project_folder_summaries", value = {
            "Read persisted folder summary artifacts for a project path."
    })
    public String readProjectFolderSummaries(final String projectPath) {
        return withTelemetry("read_project_folder_summaries", projectPath, () -> {
            final ProjectFolder project = requireProject(projectPath);
            final List<ProjectKnowledge> entries = store.listByPrefix(project, FOLDER_SUMMARY_PREFIX);
            if (entries.isEmpty()) {
                return "No folder summaries are available yet for: " + project.getDirectory();
            }

            final StringBuilder sb = new StringBuilder(4096);
            sb.append("project: ").append(project.getDirectory()).append('\n');
            sb.append("folder_summaries:\n");
            for (final ProjectKnowledge entry : entries) {
                sb.append("\n");
                sb.append("=== ").append(entry.getKey()).append(" ===\n");
                sb.append(entry.getJsonContent()).append('\n');
            }
            return sb.toString();
        });
    }

    @Tool(name = "read_project_risk_points", value = {
            "Extract risk-oriented points from the latest project summary report."
    })
    public String readProjectRiskPoints(final String projectPath) {
        return withTelemetry("read_project_risk_points", projectPath, () -> {
            final ProjectFolder project = requireProject(projectPath);
            final String report = store.read(project, PROJECT_SUMMARY_KEY);
            if (report == null || report.isBlank()) {
                return "No project summary report is available yet for: " + project.getDirectory();
            }
            return report;
        });
    }

    @Tool(name = "read_project_retrieved_context", value = {
            "Run semantic retrieval for a project and query string over indexed chunks."
    })
    public String readProjectRetrievedContext(final String projectPath, final String query) {
        return withTelemetry("read_project_retrieved_context", projectPath, () -> {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query must not be blank");
            }
            final ProjectFolder project = requireProject(projectPath);
            final String context = embeddingRetrievalService.retrieveContext(project, query, 8);
            return context == null || context.isBlank()
                    ? "No semantic retrieval context is available yet for: " + project.getDirectory()
                    : context;
        });
    }

    @Tool(name = "read_project_graph_context", value = {
            "Run graph-based retrieval context for a project and query string."
    })
    public String readProjectGraphContext(final String projectPath, final String query) {
        return withTelemetry("read_project_graph_context", projectPath, () -> {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query must not be blank");
            }
            final ProjectFolder project = requireProject(projectPath);
            final String context = graphRagPipelineService.retrieveGraphContext(project, query, 8);
            return context == null || context.isBlank()
                    ? "No graph retrieval context is available yet for: " + project.getDirectory()
                    : context;
        });
    }

    private ProjectFolder requireProject(final String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("projectPath must not be blank");
        }
        final String resolved = cwdService.resolveDirectoryPath(projectPath);
        final Path normalized = Path.of(resolved).toAbsolutePath().normalize();

        final List<ProjectFolder> projects = ProjectFolder.listAll();
        for (final ProjectFolder project : projects) {
            if (project.getDirectory() == null || project.getDirectory().isBlank()) {
                continue;
            }
            final Path projectRoot = Path.of(project.getDirectory()).toAbsolutePath().normalize();
            if (projectRoot.equals(normalized)) {
                return project;
            }
        }
        throw new IllegalArgumentException("Project root not found for path: " + normalized);
    }

    private String withTelemetry(final String toolName, final String projectPath, final ToolOperation op) {
        final long started = System.nanoTime();
        String outputPayload = null;
        String errorClass = null;
        boolean argumentValidationFailure = false;
        try {
            outputPayload = op.execute();
            return outputPayload;
        } catch (final IllegalArgumentException e) {
            argumentValidationFailure = true;
            errorClass = e.getClass().getName();
            outputPayload = "Error: " + e.getMessage();
            return outputPayload;
        } catch (final RuntimeException e) {
            errorClass = e.getClass().getName();
            outputPayload = "Error: " + e.getMessage();
            return outputPayload;
        } finally {
            if (telemetryService != null) {
                telemetryService.recordToolUse(
                        toolName,
                        "PROJECT_SUMMARY",
                        "projectPath=" + projectPath,
                        outputPayload,
                        Math.max(0L, System.nanoTime() - started),
                        errorClass,
                        argumentValidationFailure);
            }
        }
    }

    @FunctionalInterface
    private interface ToolOperation {
        String execute();
    }
}
