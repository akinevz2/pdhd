package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SummaryFormattingServiceTest {

    @Test
    void safeTextRemovesEvidenceScaffolding() {
        final SummaryFormattingService service = new SummaryFormattingService();

        final String cleaned = service.safeText(
                "=== Folder: demo ===\n\nUseful summary\n\n--- File: src/main/App.java ---\nMore detail (evidence only)...(truncated)");

        assertFalse(cleaned.contains("=== Folder:"));
        assertFalse(cleaned.contains("--- File:"));
        assertFalse(cleaned.contains("evidence only"));
        assertFalse(cleaned.contains("truncated"));
        assertTrue(cleaned.contains("Useful summary"));
        assertTrue(cleaned.contains("More detail"));
    }
}