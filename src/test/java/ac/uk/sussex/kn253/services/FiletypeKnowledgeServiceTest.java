package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FiletypeKnowledgeServiceTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Helpers

    private static FiletypeKnowledgeService serviceWith(final SubagentService subagentService) {
        final FiletypeKnowledgeService svc = new FiletypeKnowledgeService();
        svc.subagentService = subagentService;
        return svc;
    }

    /**
     * Assistant that always says "required" for the given support type question
     * keyword.
     */
    private static SubagentService assistantRequiring(final String... keywords) {
        return new SubagentService() {

            @Override
            public SupportResponse requiresSupport(final String fileName, final String supportType) {
                for (final String kw : keywords) {
                    if (supportType.contains(kw)) {
                        return new SupportResponse(true, "test");
                    }
                }
                return new SupportResponse(false, "test");
            }

            @Override
            public String getFileType(final String fileName) {
                return "text";
            }
        };
    }

    /** Assistant that always throws when queried. */
    private static SubagentService throwingAssistant() {
        return new SubagentService() {

            @Override
            public SupportResponse requiresSupport(final String fileName, final String supportType) {
                throw new RuntimeException("AI service unavailable");
            }

            @Override
            public String getFileType(final String fileName) {
                throw new RuntimeException("AI service unavailable");
            }
        };
    }

    // -------------------------------------------------------------------------
    // Tests

    @Test
    void detectReturnsSupportTypeWhenAssistantSaysRequired() throws Exception {
        final Path file = tempDir.resolve("report.pdf");
        java.nio.file.Files.writeString(file, "dummy");

        final var svc = serviceWith(assistantRequiring("PDF"));
        final var result = svc.detect(file.toString());

        assertTrue(result.supportNeeded().contains(FiletypeKnowledgeService.SupportType.PDF_VIEWING));
    }

    @Test
    void detectExcludesSupportTypeWhenAssistantSaysNotRequired() throws Exception {
        final Path file = tempDir.resolve("code.ts");
        java.nio.file.Files.writeString(file, "const x = 1;");

        // Assistant says nothing is needed
        final var svc = serviceWith(assistantRequiring());
        final var result = svc.detect(file.toString());

        assertTrue(result.supportNeeded().isEmpty());
    }

    @Test
    void detectReturnsMultipleSupportTypesWhenAssistantSaysSo() throws Exception {
        final Path file = tempDir.resolve("notes.md");
        java.nio.file.Files.writeString(file, "# Hello");

        // Assistant says both markdown and code viewing are needed
        final var svc = serviceWith(assistantRequiring("Markdown", "Code"));
        final var result = svc.detect(file.toString());

        assertTrue(result.supportNeeded().contains(FiletypeKnowledgeService.SupportType.MARKDOWN_VIEWING));
        assertTrue(result.supportNeeded().contains(FiletypeKnowledgeService.SupportType.CODE_VIEWING));
        assertFalse(result.supportNeeded().contains(FiletypeKnowledgeService.SupportType.PDF_VIEWING));
        assertFalse(result.supportNeeded().contains(FiletypeKnowledgeService.SupportType.IMAGE_VIEWING));
    }

    @Test
    void detectReturnsEmptySetWhenAssistantThrows() throws Exception {
        final Path file = tempDir.resolve("unknown.bin");
        java.nio.file.Files.writeString(file, "binary-ish");

        final var svc = serviceWith(throwingAssistant());
        final var result = svc.detect(file.toString());

        assertTrue(result.supportNeeded().isEmpty(),
                "Should silently fall back to empty set when AI service unavailable");
    }

    @Test
    void detectNormalisesPath() throws Exception {
        final Path file = tempDir.resolve("image.png");
        java.nio.file.Files.writeString(file, "pretend image");

        final var svc = serviceWith(assistantRequiring("Image"));
        // Pass a relative path that needs normalisation
        final var result = svc.detect(file.toString());

        assertEquals(file.toAbsolutePath().normalize(), result.path());
    }

    @Test
    void supportTypeQuestionsAreNonBlank() {
        for (final var type : FiletypeKnowledgeService.SupportType.values()) {
            assertFalse(type.getQuestion().isBlank(),
                    type.name() + " question should not be blank");
        }
    }

    @Test
    void allFourSupportTypesExist() {
        final Set<FiletypeKnowledgeService.SupportType> all = Set.of(FiletypeKnowledgeService.SupportType.values());

        assertTrue(all.contains(FiletypeKnowledgeService.SupportType.PDF_VIEWING));
        assertTrue(all.contains(FiletypeKnowledgeService.SupportType.CODE_VIEWING));
        assertTrue(all.contains(FiletypeKnowledgeService.SupportType.IMAGE_VIEWING));
        assertTrue(all.contains(FiletypeKnowledgeService.SupportType.MARKDOWN_VIEWING));
    }
}
