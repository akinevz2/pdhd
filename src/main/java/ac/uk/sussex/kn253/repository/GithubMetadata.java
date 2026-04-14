package ac.uk.sussex.kn253.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * JPA entity that stores GitHub repository metadata (name, description, and
 * the canonical HTTPS URL) retrieved via the {@code gh} CLI.
 */
@Entity
@Table(name = "github_metadata")
public class GithubMetadata extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = true)
    private String name;
    @Column(nullable = true, length = 1024)
    private String description;
    @Column(nullable = true, length = 2048)
    private String repoUrl;

    public GithubMetadata() {
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(final String repoUrl) {
        this.repoUrl = repoUrl;
    }
}
