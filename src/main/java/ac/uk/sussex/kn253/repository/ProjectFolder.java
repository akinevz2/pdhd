package ac.uk.sussex.kn253.repository;

import java.nio.file.Path;
import java.util.*;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * JPA entity representing a discovered software project.
 *
 * <p>
 * Each {@code ProjectFolder} stores the absolute path to its root directory,
 * optional references to persisted Git and GitHub metadata, and an optional
 * UUID-to-path index for discovered child folders.
 */
@Entity
public class ProjectFolder extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    private String directory;

    @OneToOne
    @JoinColumn(nullable = true)
    private GithubMetadata githubRepository;

    @OneToOne
    @JoinColumn(nullable = true)
    private GitFolder gitRepository;

    @Column(nullable = false)
    private boolean loaded;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_folder_contained_paths", joinColumns = @JoinColumn(name = "project_folder_id"))
    @MapKeyColumn(name = "folder_uuid", length = 36)
    @Column(name = "folder_path", nullable = false, length = 2048)
    private Map<String, String> containedFolderPaths = new LinkedHashMap<>();

    /**
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(final Long id) {
        this.id = id;
    }

    /**
     * @return the directory
     */
    public String getDirectory() {
        return directory;
    }

    /**
     * @param directory the directory to set
     */
    public void setDirectory(final String directory) {
        this.directory = directory;
    }

    /**
     * @return the githubRepository
     */
    public GithubMetadata getGithubRepository() {
        return githubRepository;
    }

    /**
     * @param githubRepository the githubRepository to set
     */
    public void setGithubRepository(final GithubMetadata githubRepository) {
        this.githubRepository = githubRepository;
    }

    /**
     * @return the gitRepository
     */
    public GitFolder getGitRepository() {
        return gitRepository;
    }

    /**
     * @param gitRepository the gitRepository to set
     */
    public void setGitRepository(final GitFolder gitRepository) {
        this.gitRepository = gitRepository;
    }

    /**
     * @return the loaded
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * @param loaded the loaded to set
     */
    public void setLoaded(final boolean loaded) {
        this.loaded = loaded;
    }

    /**
     * @return persisted folder index as UUID-string to relative path.
     */
    public Map<String, String> getContainedFolderPaths() {
        return containedFolderPaths;
    }

    /**
     * @param containedFolderPaths persisted folder index as UUID-string to path.
     */
    public void setContainedFolderPaths(final Map<String, String> containedFolderPaths) {
        this.containedFolderPaths = containedFolderPaths == null ? new LinkedHashMap<>() : containedFolderPaths;
    }

    /**
     * Stores a folder mapping using logical UUID -> path semantics.
     */
    public void putContainedFolderPath(final UUID folderId, final Path folderPath) {
        if (folderId == null || folderPath == null) {
            return;
        }
        containedFolderPaths.put(folderId.toString(), folderPath.toString());
    }

    /**
     * Resolves a contained folder path by UUID.
     */
    public Path getContainedFolderPath(final UUID folderId) {
        if (folderId == null) {
            return null;
        }
        final String rawPath = containedFolderPaths.get(folderId.toString());
        return rawPath == null || rawPath.isBlank() ? null : Path.of(rawPath);
    }

}
