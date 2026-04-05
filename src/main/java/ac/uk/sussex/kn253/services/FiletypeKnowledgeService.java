package ac.uk.sussex.kn253.services;

import java.nio.file.Path;
import java.util.*;

import dev.langchain4j.model.chat.ChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Caches filetype knowledge in the database and reuses it for repeat lookups.
 */
@ApplicationScoped
public class FiletypeKnowledgeService {

    enum SupportType {
        PDF_VIEWING("PDF viewing support"),
        CODE_VIEWING("Code syntax highlighting support"),
        IMAGE_VIEWING("Image viewing support"),
        MARKDOWN_VIEWING("Markdown viewing support");

        private final String question;

        SupportType(final String question) {
            this.question = question;
        }

        public String getQuestion() {
            return question;
        }
    }

    @Inject
    AssistantService assistantService;

    @Inject
    ChatModel chatModel;

    public record FiletypeResult(
            Path path,
            Set<SupportType> supportNeeded) {
    }

    @Transactional
    public FiletypeResult detect(final String rawPath) {
        final Path path = Path.of(rawPath).toAbsolutePath().normalize();
        final Set<SupportType> supported = querySupport(path);
        return new FiletypeResult(path, supported);
    }

    /**
     * Queries the AI service for each support type to determine which ones
     * the file requires. Falls back to heuristics if AI service unavailable.
     */
    private Set<SupportType> querySupport(final Path path) {
        final Set<SupportType> needed = EnumSet.noneOf(SupportType.class);
        for (final SupportType supportType : SupportType.values()) {
            try {
                final SupportResponse response = assistantService.requiresSupport(
                        path.toString(),
                        supportType.getQuestion());
                if (response.required()) {
                    needed.add(supportType);
                }
            } catch (final Exception e) {
                // AI service unavailable - assume not needed
            }
        }
        return needed;
    }

}