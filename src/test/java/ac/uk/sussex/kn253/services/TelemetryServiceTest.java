package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TelemetryServiceTest {

    @Test
    void clipTextReturnsNullWhenInputNull() {
        assertNull(TelemetryService.clipText(null));
    }

    @Test
    void clipTextKeepsShortValues() {
        assertEquals("abc", TelemetryService.clipText("abc"));
    }

    @Test
    void clipTextTruncatesLongValues() {
        final String longValue = "x".repeat(TelemetryService.MAX_TEXT_LENGTH + 25);
        final String clipped = TelemetryService.clipText(longValue);

        assertEquals(TelemetryService.MAX_TEXT_LENGTH, clipped.length());
        assertEquals("x".repeat(TelemetryService.MAX_TEXT_LENGTH), clipped);
    }
}