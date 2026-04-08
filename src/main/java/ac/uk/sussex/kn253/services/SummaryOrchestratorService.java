package ac.uk.sussex.kn253.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import ac.uk.sussex.kn253.repository.ProjectFolder;
import ac.uk.sussex.kn253.services.ai.RaftProjectAnalysisService;
import ac.uk.sussex.kn253.tools.ProjectSummaryTools;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Coordinates the application's core inspection workflow.
 *
 * <p>
 * PDHD explores the local filesystem, documents folders and registered
 * projects,
 * and persists those findings so known projects can be re-inspected later with
 * stronger context. Folder analysis stays local to the requested path, while
 * registered project analysis aggregates folder summaries and then produces a
 * project-level report grounded in recalled evidence.
 */
@ApplicationScoped
public class SummaryOrchestratorService {

    private static final int RAFT_CONTEXT_LIMIT = 10;
    private static final String FOLDER_SUMMARY_REQUEST = "Document the contents and purpose of this folder in concise factual terms.";
    private static final String PROJECT_SUMMARY_REQUEST = "Document this local software project as a structured markdown report. Focus on project purpose, major folders, technologies, notable implementation state, and evidence grounded in the retrieved project context.";
    private static final String PROJECT_NEXT_STEPS_REQUEST = "Estimate the most plausible next implementation steps for this project. Prioritise explicit, actionable steps grounded in the current project evidence and explain dependencies where possible.";

    @Inject
    CwdService cwdService;

    @Inject
    FolderSummaryService folderSummaryService;

    @Inject
    RaftProjectAnalysisService raftProjectAnalysisService;

    @Inject
    EmbeddingRetrievalService embeddingRetrievalService;

    @Inject
    ProjectKnowledgeSummaryStoreService store;

    @Inject
    RagPolicyService ragPolicyService;

    public record SummaryResult(String reply, String scope, String resolvedPath) {
    }

    public SummaryResult summarize(final String rawPath) {
        final Path resolved = resolveDirectory(rawPath);
        final ProjectFolder project = findProjectByRoot(resolved);
        if (project == null) {
            final String folderSummary = summarizeFolderPath(resolved);
            return new SummaryResult(folderSummary, "folder", resolved.toString());
        }

        final String projectReport = summarizeProjectPath(project, resolved);
        return new SummaryResult(projectReport, "project", resolved.toString());
    }

    public SummaryResult nextSteps(final String rawPath) {
        final Path resolved = resolveDirectory(rawPath);
        final ProjectFolder project = findProjectByRoot(resolved);
        if (project == null) {
            throw new WebApplicationException(
                    "Next steps can only be requested for a registered project root path",
                    Response.Status.BAD_REQUEST);
        }

        ensureProjectSummaryExists(project, resolved);
        final RaftReasoning raftResult = analyzeProject(project, resolved, PROJECT_NEXT_STEPS_REQUEST, true);
        final String nextSteps = raftAnswer(raftResult);
        final String cleaned = sanitizeNextSteps(nextSteps);
        persist(project, ProjectSummaryTools.PROJECT_NEXT_STEPS_KEY, cleaned);
        return new SummaryResult(cleaned, "project", resolved.toString());
    }

    @Transactional
    String summarizeProjectPath(final ProjectFolder project, final Path root) {
        final List<Path> folders = discoverProjectFolders(root);
        for (final Path folder : folders) {
            final String summary = summarizeFolderPath(folder);
            persist(project, folderSummaryKey(folder), summary);
        }

        final RaftReasoning raftResult = analyzeProject(project, root, PROJECT_SUMMARY_REQUEST, false);
        final String cleaned = stripNextStepsSections(raftAnswer(raftResult));
        persist(project, ProjectSummaryTools.PROJECT_SUMMARY_KEY, cleaned);
        return cleaned;
    }

    private void ensureProjectSummaryExists(final ProjectFolder project, final Path root) {
        final String existing = store.read(project, ProjectSummaryTools.PROJECT_SUMMARY_KEY);
        if (existing == null || existing.isBlank()) {
            summarizeProjectPath(project, root);
        }
    }

    private String summarizeFolderPath(final Path folder) {
        final String result = folderSummaryService.summarizeFolder(FOLDER_SUMMARY_REQUEST, folder.toString());
        return stripNextStepsSections(result);
    }

    private RaftReasoning analyzeProject(final ProjectFolder project, final Path root, final String request,
            final boolean nextSteps) {
        final EmbeddingRetrievalService.RaftContext raftContext = embeddingRetrievalService.retrieveRaftContext(
                project,
                request,
                RAFT_CONTEXT_LIMIT);
        return nextSteps
                ? raftProjectAnalysisService.generateNextSteps(request, root.toString(), raftContext.combined())
                : raftProjectAnalysisService.summarizeProject(request, root.toString(), raftContext.combined());
    }

    private String raftAnswer(final RaftReasoning raftResult) {
        if (raftResult == null || raftResult.answer() == null) {
            return "";
        }
        return raftResult.answer().trim();
    }

    private Path resolveDirectory(final String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new WebApplicationException("Path is required", Response.Status.BAD_REQUEST);
        }
        final String resolved = cwdService.resolveDirectoryPath(rawPath.trim());
        return Path.of(resolved).toAbsolutePath().normalize();
    }

    private ProjectFolder findProjectByRoot(final Path path) {
        final List<ProjectFolder> projects = ProjectFolder.listAll();
        for (final ProjectFolder project : projects) {
            if (project.getDirectory() == null || project.getDirectory().isBlank()) {
                continue;
            }
            final Path root = Path.of(project.getDirectory()).toAbsolutePath().normalize();
            if (root.equals(path)) {
                return project;
            }
        }
        return null;
    }

    private List<Path> discoverProjectFolders(final Path root) {
        final Set<Path> selected = new LinkedHashSet<>();
        selected.add(root);
        try (var children = Files.list(root)) {
            children
                    .filter(Files::isDirectory)
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .filter(this::isSummarisableFolder)
                    .sorted()
                    .limit(20)
                    .forEach(selected::add);
        } catch (final IOException e) {
            throw new WebApplicationException(
                    "Unable to enumerate project folders: " + e.getMessage(),
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
        return new ArrayList<>(selected);
    }

    private boolean isSummarisableFolder(final Path folder) {
        final Path fileName = folder.getFileName();
        if (fileName == null) {
            return false;
        }
        return !ragPolicyService.isIgnorableFolderName(fileName.toString());
    }

    private void persist(final ProjectFolder project, final String key, final String content) {
        final String wrapped = "generatedAt=" + Instant.now() + "\n\n" + (content == null ? "" : content);
        store.upsert(project, key, wrapped);
    }

    private String folderSummaryKey(final Path folder) {
        return ProjectSummaryTools.FOLDER_SUMMARY_PREFIX + folder.toString();
    }

    private String sanitizeNextSteps(final String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(?im)^#+\\s*(recommendations?|action plan)\\s*$", "").trim();
    }

    private String stripNextStepsSections(final String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        final String[] lines = markdown.split("\\R", -1);
        final StringBuilder sb = new StringBuilder(markdown.length());
        boolean skipping = false;
        for (final String line : lines) {
            final String trimmed = line.trim().toLowerCase(Locale.ROOT);
            final boolean heading = trimmed.startsWith("#");
            if (heading && (trimmed.contains("next step") || trimmed.contains("recommendation")
                    || trimmed.contains("action plan"))) {
                skipping = true;
                continue;
            }
            if (heading && skipping) {
                skipping = false;
            }
            if (!skipping && !trimmed.equals("next steps:")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }
}
