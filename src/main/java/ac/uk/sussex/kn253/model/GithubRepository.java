package ac.uk.sussex.kn253.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GithubRepository extends PanacheEntity {
    @Column(nullable = true)
    private String name;
    @Column(nullable = true)
    private String description;
}
