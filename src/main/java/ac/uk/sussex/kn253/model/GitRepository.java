package ac.uk.sussex.kn253.model;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GitRepository extends PanacheEntity {
    @ElementCollection(fetch = FetchType.EAGER)
    List<Origin> origins;
}
