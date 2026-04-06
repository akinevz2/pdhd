package ac.uk.sussex.kn253;

import ac.uk.sussex.kn253.services.RuntimeManagementService;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PdhdLauncher implements QuarkusApplication {

    @Inject
    @TopCommand
    MainMenu mainMenu;

    @Inject
    RuntimeManagementService runtimeManagementService;

    @Override
    public int run(final String... args) throws Exception {
        if (args != null && args.length > 0) {
            final int exitCode = mainMenu.execute(args);
            if ("webui".equals(args[0])) {
                Quarkus.waitForExit();
            }
            return exitCode;
        }

        // Start TUI menu on a background thread after Quarkus has fully booted.
        final Thread menuThread = Thread.ofVirtual()
                .name("pdhd-main-menu")
                .uncaughtExceptionHandler((thread, throwable) -> {
                    throwable.printStackTrace();
                    Quarkus.asyncExit(1);
                })
                .start(mainMenu);
        runtimeManagementService.registerMenuThread(menuThread);
        Quarkus.waitForExit();
        return 0;
    }
}
