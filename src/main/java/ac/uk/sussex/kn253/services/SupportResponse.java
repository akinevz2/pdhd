package ac.uk.sussex.kn253.services;

import dev.langchain4j.model.output.structured.Description;

/**
 * Structured response from SubagentService for support type queries.
 * LangChain4j uses @Description annotations to generate JSON schema
 * when RESPONSE_FORMAT_JSON_SCHEMA capability is enabled on OllamaChatModel.
 */
@Description("Whether a file requires a specific type of support")
public record SupportResponse(
        @Description("Whether the file requires this type of support") boolean required,

        @Description("Brief explanation for the decision") String reasoning) {
}
