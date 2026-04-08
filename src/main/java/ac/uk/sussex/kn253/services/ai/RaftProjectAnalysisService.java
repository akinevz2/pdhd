package ac.uk.sussex.kn253.services.ai;

import ac.uk.sussex.kn253.services.RaftReasoning;
import ac.uk.sussex.kn253.tools.ProjectSummaryTools;
import dev.langchain4j.service.*;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

/**
 * AI service contract for RAFT-style project analysis.
 *
 * <p>
 * RAFT (Retrieval Augmented Fine-Tuning) methodology requires the model to:
 * <ol>
 * <li>Discriminate between <em>oracle documents</em> (high-similarity, likely
 * relevant) and <em>distractor documents</em> (lower-similarity, potentially
 * irrelevant) supplied in the retrieved context.</li>
 * <li>Ground its answers in verbatim evidence quoted with
 * {@code ##begin_quote## ... ##end_quote##} markers.</li>
 * <li>Reason step-by-step from quoted evidence before producing a final
 * answer.</li>
 * </ol>
 *
 * <p>
 * Methods return {@link RaftReasoning}, a structured record whose JSON schema
 * is derived automatically by LangChain4j from its {@link
 * dev.langchain4j.model.output.structured.Description} annotations. Ollama is
 * constrained to match that schema because the producing {@code ChatModel} bean
 * has {@code RESPONSE_FORMAT_JSON_SCHEMA} capability enabled.
 *
 * <p>
 * Callers extract {@link RaftReasoning#answer()} for the final user-facing
 * output, and may log or persist {@link RaftReasoning#reasoning()} and
 * {@link RaftReasoning#evidenceQuotes()} for explainability.
 */
@RegisterAiService
public interface RaftProjectAnalysisService {

    @SystemMessage("""
            You are a domain-specific software project analyst applying RAFT \
            (Retrieval Augmented Fine-Tuning) methodology.

            You have been provided with ORACLE DOCUMENTS (high-similarity, likely relevant) \
            and DISTRACTOR DOCUMENTS (lower-similarity, may be irrelevant). \
            You must treat them as follows:

              1. Read each oracle document and judge whether it truly answers the question.
              2. Discard any distractor document that does not contribute useful evidence.
              3. For every fact you rely on, quote the exact passage verbatim: \
                 ##begin_quote## <exact text> ##end_quote##
              4. Reason from the quoted evidence to conclusions, step by step.
              5. Before writing the final answer, call read_project_folder_summaries \
                 with project path {{projectPath}} to include recalled folder findings.

            Retrieved context (oracle + distractor documents):
            {{retrievedContext}}

            Produce a structured markdown report. \
            Do NOT include next steps, recommendations, or action plans.
            """)
    @ToolBox({ ProjectSummaryTools.class })
    RaftReasoning summarizeProject(
            @UserMessage String request,
            @V("projectPath") String projectPath,
            @V("retrievedContext") String retrievedContext);

    @SystemMessage("""
            You are a practical software planning assistant applying RAFT \
            (Retrieval Augmented Fine-Tuning) methodology.

            You have been provided with ORACLE DOCUMENTS (high-similarity, likely relevant) \
            and DISTRACTOR DOCUMENTS (lower-similarity, may be irrelevant). \
            You must treat them as follows:

              1. Read each oracle document and judge whether it is relevant to planning.
              2. Discard distractor documents that do not contribute to actionable recommendations.
              3. For every fact you rely on, quote the exact passage verbatim: \
                 ##begin_quote## <exact text> ##end_quote##
              4. Reason from quoted evidence to concrete, prioritised next steps.
              5. Before writing the final answer, call read_project_summary_report \
                 and read_project_folder_summaries with project path {{projectPath}}.

            Retrieved context (oracle + distractor documents):
            {{retrievedContext}}

            Generate prioritised, actionable next steps. \
            Include rationale and dependencies for each step.
            """)
    @ToolBox({ ProjectSummaryTools.class })
    RaftReasoning generateNextSteps(
            @UserMessage String request,
            @V("projectPath") String projectPath,
            @V("retrievedContext") String retrievedContext);
}
