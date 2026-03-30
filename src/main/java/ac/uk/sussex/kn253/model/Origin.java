package ac.uk.sussex.kn253.model;

import java.net.URL;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * JPA embeddable representing a single git remote (fetch) entry.
 * The URL is stored as a string; SCP-style SSH remotes are normalised to
 * their {@code https://} equivalent when the entity is created.
 */
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Origin {
    private static final String GITHUB_HOST = "github.com";

    @Column(nullable = false)
    String name;
    @Column(nullable = true)
    URL url;

    public boolean isGithub() {
        return url != null && GITHUB_HOST.equalsIgnoreCase(url.getHost());
    }

}
