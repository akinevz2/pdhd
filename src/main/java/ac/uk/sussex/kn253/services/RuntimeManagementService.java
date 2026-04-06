package ac.uk.sussex.kn253.services;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Manages application state to keep Quarkus running indefinitely until explicit
 * shutdown.
 */
@ApplicationScoped
public class RuntimeManagementService {

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final AtomicReference<Thread> menuThreadRef = new AtomicReference<>();

    public void registerMenuThread(final Thread thread) {
        if (thread != null) {
            menuThreadRef.set(thread);
        }
    }

    public void requestShutdown() {
        final Thread menuThread = menuThreadRef.get();
        if (menuThread != null && menuThread.isAlive()) {
            menuThread.interrupt();
        }
        shutdownLatch.countDown();
        Quarkus.asyncExit();
    }

    public void blockUntilShutdown() throws InterruptedException {
        // Block until requestShutdown() is called
        shutdownLatch.await();
    }
}