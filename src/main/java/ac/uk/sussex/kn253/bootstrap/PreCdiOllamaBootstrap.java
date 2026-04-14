package ac.uk.sussex.kn253.bootstrap;

import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Pre-CDI bootstrap that verifies the Ollama endpoint is reachable before
 * Quarkus CDI comes online.
 */
public final class PreCdiOllamaBootstrap {

    private static final Logger LOG = Logger.getLogger(PreCdiOllamaBootstrap.class.getName());

    private static final String KEY_BASE_URL = "pdhd.ollama.base-url";
    private static final String BOOTSTRAP_BASE_URL = "pdhd.ollama.bootstrap.base-url";

    private PreCdiOllamaBootstrap() {
    }

    public static void prepareForLaunch(final String[] args) {
        prepareForLaunch(args, new DefaultOps());
    }

    static void prepareForLaunch(final String[] args, final StartupOps ops) {
        if (isHelpOrVersion(args)) {
            return;
        }

        final BootstrapConfig config = BootstrapConfig.load();
        final String baseUrl = trimToNull(config.baseUrl());

        if (baseUrl == null) {
            throw new IllegalStateException("Ollama base URL is not configured");
        }

        if (!ops.isHealthy(baseUrl)) {
            throw new IllegalStateException("Configured Ollama endpoint is unreachable: " + baseUrl);
        }

        System.setProperty(KEY_BASE_URL, baseUrl);
        System.setProperty(BOOTSTRAP_BASE_URL, baseUrl);
        LOG.info(() -> "Pre-CDI bootstrap: Ollama reachable at " + baseUrl);
    }

    private static boolean isHelpOrVersion(final String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        final String command = args[0];
        if (command == null || command.isBlank()) {
            return false;
        }
        return "-h".equals(command)
                || "--help".equals(command)
                || "-V".equals(command)
                || "--version".equals(command)
                || "help".equalsIgnoreCase(command);
    }

    static String trimToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    interface StartupOps {
        boolean isHealthy(String baseUrl);
    }

    static final class DefaultOps implements StartupOps {

        private final HttpClient client = HttpClient.newHttpClient();

        @Override
        public boolean isHealthy(final String baseUrl) {
            try {
                final String endpoint = normalizeBaseUrl(baseUrl) + "/api/tags";
                final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();
                final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() == 200;
            } catch (final Exception e) {
                return false;
            }
        }

        private String normalizeBaseUrl(final String baseUrl) {
            final String trimmed = trimToNull(baseUrl);
            if (trimmed == null) {
                throw new IllegalArgumentException("Ollama base URL is not configured");
            }
            if (trimmed.endsWith("/")) {
                return trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }
    }

    static final class BootstrapConfig {
        private final String baseUrl;

        BootstrapConfig(final String baseUrl) {
            this.baseUrl = baseUrl;
        }

        static BootstrapConfig load() {
            final Properties props = new Properties();
            try (InputStream in = PreCdiOllamaBootstrap.class.getClassLoader()
                    .getResourceAsStream("application.properties")) {
                if (in != null) {
                    props.load(in);
                }
            } catch (final Exception ignored) {
                // Defaults below will apply.
            }

            final String profile = resolveProfile();
            final String baseUrl = resolveValue(KEY_BASE_URL, profile, props);
            return new BootstrapConfig(baseUrl);
        }

        String baseUrl() {
            return baseUrl;
        }

        private static String resolveProfile() {
            return firstNonBlank(
                    System.getProperty("quarkus.profile"),
                    System.getenv("QUARKUS_PROFILE"),
                    "prod");
        }

        private static String resolveValue(final String key, final String profile, final Properties props) {
            final String fromSystem = trimToNull(System.getProperty(key));
            if (fromSystem != null) {
                return fromSystem;
            }

            final String envKey = key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
            final String fromEnv = trimToNull(System.getenv(envKey));
            if (fromEnv != null) {
                return fromEnv;
            }

            final String fromProfile = trimToNull(props.getProperty("%" + profile + "." + key));
            if (fromProfile != null) {
                return fromProfile;
            }

            return trimToNull(props.getProperty(key));
        }

        private static String firstNonBlank(final String... values) {
            for (final String value : values) {
                final String trimmed = trimToNull(value);
                if (trimmed != null) {
                    return trimmed;
                }
            }
            return null;
        }
    }
}
