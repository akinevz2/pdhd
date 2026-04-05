package ac.uk.sussex.kn253.services;

import java.util.concurrent.CountDownLatch;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.runtime.Quarkus;

/**
  * Manages application state to keep Quarkus running indefinitely until explicit shutdown.
 */
@ApplicationScoped
public class StateManagementService {

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);


    public void requestShutdown() {
        shutdownLatch.countDown();
        Quarkus.asyncExit();
    }

    public void blockUntilShutdown() throws InterruptedException {
        // Block until requestShutdown() is called
        shutdownLatch.await();
    }
}
