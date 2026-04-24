package ac.uk.sussex.kn253.model;

import java.util.List;

/**
 * Structured representation of a project completion assessment.
 *
 * <p>The assessment is produced by the AI completion estimation pipeline and
 * persisted alongside the project summary.  It is intentionally simple so
 * that downstream services can consume it without complex mapping.
 */
public record CompletionAssessment(
        /**
         * A percentage score (0‑100) indicating how complete the project
         * appears to be based on the evidence gathered during inspection.
         */
        int completionScore,
        /**
         * Confidence level of the score.  The value is a short string such as
         * "High", "Medium" or "Low".
         */
        String confidenceScore,
        /**
         * A list of textual gaps or missing artefacts that the AI identified.
         * Each entry should be a concise sentence.
         */
        List<String> gapAnalysis,
        /**
         * Key assumptions that the score relies on.  These are useful for
         * audit and for explaining the assessment to users.
         */
        List<String> keyAssumptions
) {}
