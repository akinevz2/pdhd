package ac.uk.sussex.kn253.model;

import java.util.List;

import org.jspecify.annotations.NonNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity that stores the set of fetch remotes (origins) for a local Git
 * repository.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GitRepository extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "git_repository_origins", joinColumns = @JoinColumn(name = "git_repository_id"))
    List<@NonNull Origin> origins;
}
