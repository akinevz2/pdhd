package ac.uk.sussex.kn253.api.model;

/** Metadata describing one backend-provided Ollama settings form field. */
public record OllamaSettingFieldResponse(
        String key,
        String label,
        String inputType,
        String hint,
        Double min,
        Double max,
        Double step,
        boolean modelField) {
}
