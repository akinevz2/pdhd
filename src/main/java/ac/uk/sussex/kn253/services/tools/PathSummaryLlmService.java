package ac.uk.sussex.kn253.services.tools;

import java.nio.file.Path;

import ac.uk.sussex.kn253.model.OllamaSettings;
import ac.uk.sussex.kn253.ollama.OllamaChatSession;
import ac.uk.sussex.kn253.services.OllamaConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Produces concise, human-readable file/folder summaries using an isolated
 * one-shot LLM session (no tool access, no shared conversation state).
 *
 * <p>When summarization is not possible the return value will start with one of
 * the sentinel prefixes {@link #SUMMARY_ERROR_PREFIX} or
 * {@link #SUMMARY_UNAVAILABLE_PREFIX}. Callers can test for these to decide
 * whether to fall back to static analysis.
 */
@ApplicationScoped
public class PathSummaryLlmService {

    /** Prefix used when an exception prevents LLM summarization. */
    public static final String SUMMARY_ERROR_PREFIX = "Failed to summarize";

    /** Prefix used when the LLM returns a blank or null response. */
    public static final String SUMMARY_UNAVAILABLE_PREFIX = "Summary unavailable";

    @Inject
    OllamaConfigService ollamaConfigService;

    /**
     * Summarizes a path using a separate LLM conversation so the tool output can
     * be consumed by higher-level folder summarization flows.
     */
    public String summarizePath(final Path target) {
        final String analysis = PathAnalyzer.analyze(target, true);

        // Fast-fail for invalid paths and analysis errors.
        if (analysis.startsWith("Path does not exist:")
                || analysis.startsWith("Failed to analyze ")
                || analysis.startsWith("Unsupported path type:")) {
            return analysis;
        }

        try {
            final OllamaSettings settings = ollamaConfigService.load();
            final String summarizerSystemPrompt = """
                    You summarize filesystem analysis results for developers.
                    Use only the provided analysis data.
                    Return concise factual summaries in plain text.
                    Include: what this path is, key files/components, and practical relevance.
                    Do not invent files or code.
                    """;

            final String userPrompt = """
                    Summarize the following path analysis for downstream folder summarization.
                    Keep it concise and evidence-based.

                    Target path:
                    %s

                    Analysis data:
                    %s
                    """.formatted(target.toAbsolutePath().normalize(), analysis);

            final String summary = OllamaChatSession.builder()
                    .baseUrl(settings.getBaseUrl())
                    .model(settings.getModelName())
                    .timeoutSeconds(settings.getTimeoutSeconds())
                    .temperature(settings.getTemperature())
                    .numPredict(settings.getNumPredict())
                    .numCtx(settings.getNumCtx())
                    .build()
                    .setSystemPrompt(summarizerSystemPrompt)
                    .sendOneShot(userPrompt);

            if (summary == null || summary.isBlank()) {
                return SUMMARY_UNAVAILABLE_PREFIX + " for " + target + ".";
            }
            return "path=" + target.toAbsolutePath().normalize() + "\nsummary=\n" + summary.trim();
        } catch (final Exception e) {
            return SUMMARY_ERROR_PREFIX + " path via LLM for " + target + ": " + e.getMessage();
        }
    }
}
