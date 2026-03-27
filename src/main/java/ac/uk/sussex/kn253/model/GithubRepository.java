package ac.uk.sussex.kn253.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity that stores GitHub repository metadata (name and description)
 * retrieved via the {@code gh} CLI.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GithubRepository extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = true)
    private String name;
    @Column(nullable = true)
    private String description;
}
