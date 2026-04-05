package ac.uk.sussex.kn253.menu;

import java.io.IOException;

@FunctionalInterface
public interface MenuCallback {
    void call() throws IOException;
}
