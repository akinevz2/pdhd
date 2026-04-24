package ac.uk.sussex.kn253;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PdhdLauncherTest {

    @Test
    void bootstrapRunsForDefaultAndWebUiLaunches() {
        assertTrue(PdhdLauncher.shouldRunBootstrap(null));
        assertTrue(PdhdLauncher.shouldRunBootstrap(new String[0]));
        assertTrue(PdhdLauncher.shouldRunBootstrap(new String[] { "webui" }));
    }

    @Test
    void bootstrapIsSkippedForConfigureAndHelpFlags() {
        assertFalse(PdhdLauncher.shouldRunBootstrap(new String[] { "configure" }));
        assertFalse(PdhdLauncher.shouldRunBootstrap(new String[] { "--help" }));
        assertFalse(PdhdLauncher.shouldRunBootstrap(new String[] { "-h" }));
        assertFalse(PdhdLauncher.shouldRunBootstrap(new String[] { "--version" }));
        assertFalse(PdhdLauncher.shouldRunBootstrap(new String[] { "-V" }));
        assertFalse(PdhdLauncher.shouldRunBootstrap(new String[] { "help" }));
    }
}