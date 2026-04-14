package ac.uk.sussex.kn253.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
public class Workspace extends PanacheEntityBase {
    @Id
    public Long id;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public List<ProjectFolder> projectFolders;
}
