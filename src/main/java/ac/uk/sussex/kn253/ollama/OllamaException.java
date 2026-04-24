package ac.uk.sussex.kn253.ollama;

/**
 * Unchecked exception thrown when an Ollama management or inference operation
 * fails.
 */
public class OllamaException extends RuntimeException {

    public OllamaException(final String message) {
        super(message);
    }

    public OllamaException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
