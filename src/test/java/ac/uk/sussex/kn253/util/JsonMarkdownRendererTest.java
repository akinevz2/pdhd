package ac.uk.sussex.kn253.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class JsonMarkdownRendererTest {

    private final JsonMarkdownRenderer renderer = new JsonMarkdownRenderer(new ObjectMapper());

    private record DemoDoc(String folderPath, List<String> probableTechnologies, List<KeyFact> keyFacts, String note) {
    }

    private record KeyFact(String relativePath, String role) {
    }

    @Test
    void renderProducesHeadingsBulletsAndInlineObjectFields() {
        final DemoDoc doc = new DemoDoc(
                "/tmp/project",
                List.of("Java", "Quarkus"),
                List.of(new KeyFact("src/main/java", "application code")),
                "inspection-ready");

        final String markdown = renderer.render(doc, "Folder Summary");

        assertTrue(markdown.startsWith("## Folder Summary"));
        assertTrue(markdown.contains("### Folder Path"));
        assertTrue(markdown.contains("/tmp/project"));
        assertTrue(markdown.contains("### Probable Technologies"));
        assertTrue(markdown.contains("- Java"));
        assertTrue(markdown.contains("- Quarkus"));
        assertTrue(markdown.contains("### Key Facts"));
        assertTrue(markdown.contains("**Relative Path**: src/main/java"));
        assertTrue(markdown.contains("**Role**: application code"));
    }

    @Test
    void renderOutputsNoneForEmptyArrays() {
        final DemoDoc doc = new DemoDoc("/tmp/project", List.of(), List.of(), "");

        final String markdown = renderer.render(doc, null);

        assertTrue(markdown.contains("### Probable Technologies\n\n_None._"));
        assertTrue(markdown.contains("### Key Facts\n\n_None._"));
    }

    @Test
    void formatKeyConvertsCamelAndSnakeCaseToTitleCase() {
        assertEquals("Folder Path", JsonMarkdownRenderer.formatKey("folderPath"));
        assertEquals("Project summary key", JsonMarkdownRenderer.formatKey("project_summary_key"));
    }
}