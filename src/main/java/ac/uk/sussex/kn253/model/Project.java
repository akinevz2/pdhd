package ac.uk.sussex.kn253.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Project extends PanacheEntity {
    @Column
    private String directory;
    @OneToOne
    @JoinColumn(nullable = true)
    private GithubRepository githubRepository;
    @OneToOne
    @JoinColumn(nullable = true)
    private GitRepository gitRepository;
}
