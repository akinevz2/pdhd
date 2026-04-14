package ac.uk.sussex.kn253.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PreCdiOllamaBootstrapTest {

    private static final String BASE_URL_KEY = "pdhd.ollama.base-url";
    private static final String BOOTSTRAP_BASE_URL = "pdhd.ollama.bootstrap.base-url";

    @AfterEach
    void cleanup() {
        System.clearProperty(BASE_URL_KEY);
        System.clearProperty(BOOTSTRAP_BASE_URL);
    }

    @Test
    void failsWhenBaseUrlNotConfigured() {
        System.clearProperty(BASE_URL_KEY);

        final StubOps ops = new StubOps();
        final IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PreCdiOllamaBootstrap.prepareForLaunch(new String[] { "webui" }, ops));

        assertTrue(error.getMessage().contains("not configured"));
    }

    @Test
    void failsWhenBaseUrlUnreachable() {
        System.setProperty(BASE_URL_KEY, "http://host.docker.internal:11434");

        final StubOps ops = new StubOps(); // no healthy URLs registered
        final IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PreCdiOllamaBootstrap.prepareForLaunch(new String[] { "webui" }, ops));

        assertTrue(error.getMessage().contains("unreachable"));
    }

    @Test
    void succeedsAndSetsSystemPropertiesWhenReachable() {
        System.setProperty(BASE_URL_KEY, "http://host.docker.internal:11434");

        final StubOps ops = new StubOps();
        ops.healthyBaseUrls.add("http://host.docker.internal:11434");

        PreCdiOllamaBootstrap.prepareForLaunch(new String[] { "webui" }, ops);

        assertEquals("http://host.docker.internal:11434", System.getProperty(BASE_URL_KEY));
        assertEquals("http://host.docker.internal:11434", System.getProperty(BOOTSTRAP_BASE_URL));
    }

    private static final class StubOps implements PreCdiOllamaBootstrap.StartupOps {
        private final Set<String> healthyBaseUrls = new HashSet<>();

        @Override
        public boolean isHealthy(final String baseUrl) {
            return healthyBaseUrls.contains(baseUrl);
        }
    }
}
