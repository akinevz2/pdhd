package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ac.uk.sussex.kn253.services.tools.macro.ToolMacros;

class ChatServiceTest {

    // -------------------------------------------------------------------------
    // selectManifestTool
    // -------------------------------------------------------------------------

    @Test
    void selectManifestToolReturnsProjectManifestForRootFolder() {
        final ChatService service = new ChatService(new WorkingDirectoryService());
        assertEquals(ToolMacros.READ_PROJECT_MANIFEST.name(), service.selectManifestTool(true));
    }

    @Test
    void selectManifestToolReturnsFolderManifestForNonRootFolder() {
        final ChatService service = new ChatService(new WorkingDirectoryService());
        assertEquals(ToolMacros.READ_FOLDER_MANIFEST.name(), service.selectManifestTool(false));
    }

    // -------------------------------------------------------------------------
    // buildSummaryPrompt — project-root vs non-root terminology
    // -------------------------------------------------------------------------

    @Test
    void buildSummaryPromptRequestsAssistantActionBlockForProjectRoot() {
        final ChatService service = new ChatService(new WorkingDirectoryService());
        final String prompt = service.buildSummaryPrompt(true, "evidence here");

        assertTrue(prompt.contains("assistant-action"),
                "Root-folder prompt must request an assistant-action block");
        assertTrue(prompt.contains("subfolder"),
                "Root-folder prompt must refer to subfolder exploration");
        assertTrue(prompt.contains("evidence here"),
                "Prompt must include the supplied evidence verbatim");
    }

    @Test
    void buildSummaryPromptRequestsAssistantActionBlockForNonRoot() {
        final ChatService service = new ChatService(new WorkingDirectoryService());
        final String prompt = service.buildSummaryPrompt(false, "evidence here");

        assertTrue(prompt.contains("assistant-action"),
                "Non-root prompt must request an assistant-action block");
        assertTrue(prompt.contains("child folder"),
                "Non-root prompt must refer to child folder exploration");
        assertTrue(prompt.contains("evidence here"),
                "Prompt must include the supplied evidence verbatim");
    }

    @Test
    void buildSummaryPromptActionHintIsExclusiveBetweenRootAndNonRoot() {
        final ChatService service = new ChatService(new WorkingDirectoryService());
        final String rootPrompt = service.buildSummaryPrompt(true, "ev");
        final String folderPrompt = service.buildSummaryPrompt(false, "ev");

        assertFalse(rootPrompt.contains("child folder"),
                "Root prompt must not contain non-root 'child folder' wording");
        assertFalse(folderPrompt.contains("subfolder"),
                "Non-root prompt must not contain root 'subfolder' wording");
    }

    // -------------------------------------------------------------------------
    // isProjectRootDirectory — marker detection (no Panache in plain tests)
    // -------------------------------------------------------------------------

    @Test
    void projectRootSupportReturnsTrueForPomXml(@TempDir final Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        assertTrue(ProjectRootSupport.isProjectRootDirectory(tempDir));
    }

    @Test
    void projectRootSupportReturnsTrueForGitDir(@TempDir final Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve(".git"));
        assertTrue(ProjectRootSupport.isProjectRootDirectory(tempDir));
    }

    @Test
    void projectRootSupportReturnsFalseForPlainFolder(@TempDir final Path tempDir) throws Exception {
        final Path subFolder = tempDir.resolve("docs");
        Files.createDirectories(subFolder);
        assertFalse(ProjectRootSupport.isProjectRootDirectory(subFolder));
    }

    // -------------------------------------------------------------------------
    // compactEvidence
    // -------------------------------------------------------------------------

    @Test
    void compactEvidenceCollapsesConsecutiveBlankLines() {
        final ChatService service = new ChatService(new WorkingDirectoryService());
        final String input = "line1\n\n\n\nline2";
        final String result = service.compactEvidence(input);

        assertEquals("line1\n\nline2", result);
    }

    @Test
    void compactEvidenceTrimsTrailingWhitespacePerLine() {
        final ChatService service = new ChatService(new WorkingDirectoryService());
        final String input = "line1   \nline2\t\nline3";
        final String result = service.compactEvidence(input);

        assertEquals("line1\nline2\nline3", result);
    }

    @Test
    void compactEvidencePreservesAllContentLines() {
        final ChatService service = new ChatService(new WorkingDirectoryService());
        final String input = "=== folder entries ===\n- src/\n- pom.xml\n\n=== sampled ===\ncontent";
        final String result = service.compactEvidence(input);

        assertTrue(result.contains("src/"));
        assertTrue(result.contains("pom.xml"));
        assertTrue(result.contains("sampled"));
        assertTrue(result.contains("content"));
    }

    @Test
    void compactEvidenceReturnsSameReferenceWhenNothingToCompact() {
        final ChatService service = new ChatService(new WorkingDirectoryService());
        final String input = "single line";
        assertEquals(input, service.compactEvidence(input));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

}
