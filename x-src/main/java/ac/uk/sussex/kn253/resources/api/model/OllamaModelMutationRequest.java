package ac.uk.sussex.kn253.resources.api.model;

/** Request body for pull/delete actions on an Ollama model. */
public record OllamaModelMutationRequest(String modelName) {
}