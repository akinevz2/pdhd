package ac.uk.sussex.kn253.repository;

/**
 * Semantic key that classifies a {@link ProjectKnowledge} dependent entry.
 *
 * <p>
 * Each variant corresponds to a distinct provenance or role of the associated
 * byte-vector and cached embedding stored alongside a knowledge record.
 */
public enum DependentKey {

    /**
     * Substring of sentence showing pre-embedding phrase as used by the model.
     */
    LABEL,

    /**
     * From user prompt: {@code "(a) is (continuation)"} is-a clause.
     */
    CONCRETE,

    /**
     * From model tool-call or message response: {@code "(a) is (continuation)"}.
     */
    RELATIVE,

    /**
     * Last sentence of the model response that produced this Dependent.
     */
    CONTEXTUAL,

    /**
     * Span of model output identifiable as tool-call requests.
     */
    OUTCOME
}
