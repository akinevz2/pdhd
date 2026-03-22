package ac.uk.sussex.kn253.model;

import java.net.URL;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Origin {
    @Column(nullable = false)
    String name;
    @Column(nullable = true)
    String url;

    public Origin(final String name, final URL url) {
        this.name = name;
        this.url = url != null ? url.toString() : null;
    }
}
