package ac.uk.sussex.kn253.repository;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * JPA entity representing a discovered software project.
 *
 * <p>
 * Each {@code Project} stores the absolute path to its root directory and
 * optional references to its {@link GitRepository} and
 * {@link GithubFolder} metadata.
 */
@Entity
public class ProjectFolder extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column
    private String directory;
    @OneToOne
    @JoinColumn(nullable = true)
    private GithubFolder githubRepository;
    @OneToOne
    @JoinColumn(nullable = true)
    private GitFolder gitRepository;

    @Column(name = "open_in_canvas", nullable = false)
    private boolean loaded;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(final String directory) {
        this.directory = directory;
    }

    public GithubFolder getGithubRepository() {
        return githubRepository;
    }

    public void setGithubRepository(final GithubFolder githubRepository) {
        this.githubRepository = githubRepository;
    }

    public GitFolder getGitRepository() {
        return gitRepository;
    }

    public void setGitRepository(final GitFolder gitRepository) {
        this.gitRepository = gitRepository;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(final boolean loaded) {
        this.loaded = loaded;
    }
}
