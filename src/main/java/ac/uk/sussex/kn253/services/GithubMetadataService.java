package ac.uk.sussex.kn253.services;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.repository.*;
import ac.uk.sussex.kn253.support.BackendSupport;
import ac.uk.sussex.kn253.support.SchemaKeys;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Enriches {@link ProjectFolder} entities with GitHub repository metadata
 * retrieved via the {@code gh} CLI.
 *
 * <p>
 * If {@code gh} is not installed or the invocation fails for any reason,
 * no exception is propagated to callers or the frontend.
 */
@ApplicationScoped
public class GithubMetadataService {

    private static final Logger LOG = Logger.getLogger(GithubMetadataService.class);
    private static final int GH_TIMEOUT_SECONDS = BackendSupport.GH_CLI_TIMEOUT_SECONDS;

    @Inject
    ObjectMapper objectMapper;

    void onStartup(@Observes final StartupEvent event) {
        logMissingGhWarningAtStartup();
    }

    @Transactional
    void logMissingGhWarningAtStartup() {
        if (isGhInstalled()) {
            return;
        }
        LOG.warn("project contains github origins but gh cli is not installed to preview the metadata");
    }

    /**
     * Attempts to enrich the given project with GitHub metadata from the
     * {@code gh} CLI if the project has at least one GitHub-hosted origin and
     * no metadata has been stored yet.
     *
     * <p>
     * This method is a no-op (and logs nothing) when the project already has
     * stored metadata. When GitHub origins are detected but {@code gh} is not
     * installed, the method returns without modifying the project.
     *
     * @param project the project to potentially enrich (must already be managed)
     */
    @Transactional
    public void tryEnrichWithGithubMetadata(final ProjectFolder project) {
        if (project == null) {
            return;
        }
        if (project.getGithubRepository() != null) {
            // already populated — nothing to do
            return;
        }
        if (!hasGithubOrigin(project)) {
            return;
        }

        final String directory = project.getDirectory();
        if (directory == null || directory.isBlank()) {
            return;
        }

        if (!isGhInstalled()) {
            return;
        }

        final Optional<GithubMetadata> metadata = fetchFromCli(directory);
        metadata.ifPresent(meta -> {
            meta.persist();
            project.setGithubRepository(meta);
        });
    }

    private boolean hasGithubOrigin(final ProjectFolder project) {
        final GitFolder gitFolder = project.getGitRepository();
        if (gitFolder == null || gitFolder.getOrigins() == null) {
            return false;
        }
        return gitFolder.getOrigins().stream().anyMatch(Origin::isGithub);
    }

    private boolean isGhInstalled() {
        try {
            final Process process = new ProcessBuilder("gh", "--version")
                    .redirectErrorStream(true)
                    .start();
            // drain output to avoid blocking
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (final IOException e) {
            return false;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Optional<GithubMetadata> fetchFromCli(final String directory) {
        try {
            final Process process = new ProcessBuilder("gh", "repo", "view",
                    "--json", SchemaKeys.GH_NAME + "," + SchemaKeys.GH_DESCRIPTION + "," + SchemaKeys.GH_URL)
                    .directory(new File(directory))
                    .redirectErrorStream(true)
                    .start();

            final byte[] outputBytes = process.getInputStream().readAllBytes();
            final boolean finished = process.waitFor(GH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                LOG.warnf("gh repo view timed out after %d seconds for directory: %s",
                        GH_TIMEOUT_SECONDS, directory);
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                final String output = new String(outputBytes, StandardCharsets.UTF_8).trim();
                LOG.debugf("gh repo view exited with code %d for directory %s: %s",
                        process.exitValue(), directory, output);
                return Optional.empty();
            }

            final String json = new String(outputBytes, StandardCharsets.UTF_8);
            final JsonNode node = objectMapper.readTree(json);

            final GithubMetadata meta = new GithubMetadata();
            meta.setName(nullIfBlank(node.path(SchemaKeys.GH_NAME).asText(null)));
            meta.setDescription(nullIfBlank(node.path(SchemaKeys.GH_DESCRIPTION).asText(null)));
            meta.setRepoUrl(nullIfBlank(node.path(SchemaKeys.GH_URL).asText(null)));
            return Optional.of(meta);

        } catch (final IOException e) {
            LOG.warnf("Failed to invoke gh CLI for directory %s: %s", directory, e.getMessage());
            return Optional.empty();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for gh CLI");
            return Optional.empty();
        } catch (final Exception e) {
            LOG.warnf("Unexpected error fetching GitHub metadata for directory %s: %s",
                    directory, e.getMessage());
            return Optional.empty();
        }
    }

    private static String nullIfBlank(final String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
