package ac.uk.sussex.kn253;

import io.quarkus.runtime.Quarkus;

public class Main {
    public static void main(final String[] args) {
        Quarkus.run(PdhdLauncher.class, args);
    }

}
