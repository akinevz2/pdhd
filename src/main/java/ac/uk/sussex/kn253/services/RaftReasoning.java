package ac.uk.sussex.kn253.services;

import java.util.List;

import dev.langchain4j.model.output.structured.Description;

/**
 * Structured output record for RAFT (Retrieval Augmented Fine-Tuning) style
 * chain-of-thought analysis.
 *
 * <p>
 * The model is expected to:
 * <ol>
 * <li>Discriminate between oracle documents (relevant) and distractors (not
 * relevant).</li>
 * <li>Cite supporting passages from oracle documents using
 * {@code ##begin_quote## ... ##end_quote##} markers.</li>
 * <li>Reason from quoted evidence before producing a final answer.</li>
 * </ol>
 *
 * <p>
 * This record is used as the return type of {@link RaftProjectAnalysisService}
 * methods. Because the producing {@code ChatModel} bean has
 * {@code RESPONSE_FORMAT_JSON_SCHEMA} enabled, LangChain4j derives the JSON
 * schema automatically from the {@link Description} annotations and Ollama
 * constrains its response to match that schema.
 */
@Description("RAFT chain-of-thought analysis grounded in retrieved documents")
public record RaftReasoning(

        @Description("""
                Step-by-step reasoning that identifies which oracle documents are relevant \
                and cites supporting passages with ##begin_quote## ... ##end_quote## markers. \
                Distractor documents that do not contribute to the answer are explicitly discarded.""") String reasoning,

        @Description("""
                Short verbatim text fragments extracted verbatim from oracle documents \
                and used as evidence. Each entry corresponds to one ##begin_quote## block \
                in the reasoning field.""") List<String> evidenceQuotes,

        @Description("""
                Final structured markdown answer synthesised only from the cited evidence above. \
                Must be self-contained and not repeat raw quotes unless necessary for clarity.""") String answer) {
}
