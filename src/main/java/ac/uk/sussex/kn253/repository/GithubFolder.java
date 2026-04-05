package ac.uk.sussex.kn253.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * JPA entity that stores GitHub repository metadata (name and description)
 * retrieved via the {@code gh} CLI.
 */
@Entity
public class GithubFolder extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = true)
    private String name;
    @Column(nullable = true)
    private String description;

    public GithubFolder() {
    }

    public GithubFolder(final Long id, final String name, final String description) {
        this.id = id;
        this.name = name;
        this.description = description;
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
}
