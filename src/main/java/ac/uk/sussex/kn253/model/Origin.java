package ac.uk.sussex.kn253.model;

import java.net.URL;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Origin extends PanacheEntity {
    @Column(nullable = false)
    String name;
    @Column(nullable = true)
    URL url;
}
