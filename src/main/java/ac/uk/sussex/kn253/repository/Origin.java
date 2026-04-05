package ac.uk.sussex.kn253.repository;

import java.net.URL;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * JPA embeddable representing a single git remote (fetch) entry.
 * The URL is stored as a string; SCP-style SSH remotes are normalised to
 * their {@code https://} equivalent when the entity is created.
 */
@Embeddable
public class Origin {
    private static final String GITHUB_HOST = "github.com";

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
        return url != null && GITHUB_HOST.equalsIgnoreCase(url.getHost());
    }

}
