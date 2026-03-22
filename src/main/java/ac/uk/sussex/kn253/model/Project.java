package ac.uk.sussex.kn253.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Project extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column
    private String directory;
    @OneToOne
    @JoinColumn(nullable = true)
    private GithubRepository githubRepository;
    @OneToOne
    @JoinColumn(nullable = true)
    private GitRepository gitRepository;
}
