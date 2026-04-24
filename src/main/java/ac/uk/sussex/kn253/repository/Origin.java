package ac.uk.sussex.kn253.repository;

import java.net.URL;

import ac.uk.sussex.kn253.support.BackendSupport;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * JPA embeddable representing a single git remote (fetch) entry.
 * The URL is stored as a string; SCP-style SSH remotes are normalised to
 * their {@code https://} equivalent when the entity is created.
 */
@Embeddable
public class Origin {
    // Host constant is centralised in BackendSupport; kept here for quick
    // local reference but must stay in sync with BackendSupport.GITHUB_HOST.

    @Column(nullable = false)
    String name;
    @Column(nullable = true)
    URL url;

    public Origin() {
    }

    public Origin(final String name, final URL url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(final URL url) {
        this.url = url;
    }

    public boolean isGithub() {
        return url != null && BackendSupport.GITHUB_HOST.equalsIgnoreCase(url.getHost());
    }

    public boolean isGitlab() {
        return url != null && (BackendSupport.GITLAB_HOST.equalsIgnoreCase(url.getHost())
                || (url.getHost() != null && url.getHost().endsWith("." + BackendSupport.GITLAB_HOST)));
    }

    public boolean isBitbucket() {
        return url != null && BackendSupport.BITBUCKET_HOST.equalsIgnoreCase(url.getHost());
    }

    /**
     * Returns {@code true} if the origin is hosted on a recognised Git forge
     * (GitHub, GitLab, Bitbucket, Codeberg).
     */
    public boolean isKnownForge() {
        if (url == null) {
            return false;
        }
        final String host = url.getHost();
        return host != null && (host.equalsIgnoreCase(BackendSupport.GITHUB_HOST)
                || host.equalsIgnoreCase(BackendSupport.GITLAB_HOST)
                || host.endsWith("." + BackendSupport.GITLAB_HOST)
                || host.equalsIgnoreCase(BackendSupport.BITBUCKET_HOST)
                || host.equalsIgnoreCase(BackendSupport.CODEBERG_HOST));
    }

}
