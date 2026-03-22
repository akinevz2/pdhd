package ac.uk.sussex.kn253.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

/**
 * Persisted Ollama connection and model settings.
 * Only a single row is ever stored; use
 * {@link ac.uk.sussex.kn253.services.OllamaConfigService}
 * to load or save it.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class OllamaSettings extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public static final String DEFAULT_SYSTEM_PROMPT = """
            You are a helpful assistant specialised in discovering software projects on a file system.
            You analyse project completion progress, identify gaps and blockers, and provide clear
            planning information for the operator, including actionable next steps, priorities, and
            concise execution plans.
            """;

    @Column(nullable = false)
    private String baseUrl = "http://localhost:11434";

    @Column(nullable = false)
    private String modelName = "llama3.2";

    @Column(nullable = false)
    private int timeoutSeconds = 120;

    @Column(nullable = false)
    private double temperature = 0.7;

    /** Maximum tokens to generate; {@code -1} means model default. */
    @Column(nullable = false)
    private int numPredict = -1;

    /** Context window size in tokens; {@code 0} means model default. */
    @Column(nullable = false)
    private int numCtx = 0;

    @Lob
    @Column(nullable = true)
    private String systemPrompt = DEFAULT_SYSTEM_PROMPT;
}
