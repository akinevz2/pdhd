package ac.uk.sussex.kn253.menu;

import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;
import picocli.CommandLine.Command;

@ApplicationScoped
@Command(name = "exit", description = "Exit application")
public class ExitCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Exiting...");
        Quarkus.asyncExit();
        Quarkus.waitForExit();
    }
}
