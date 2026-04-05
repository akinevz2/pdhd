package ac.uk.sussex.kn253.menu;

import ac.uk.sussex.kn253.services.WebUiService;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;

@ApplicationScoped
@Command(name = "webui", description = "Launch web UI")
public class WebUiMenu implements Runnable {

    @Inject
    WebUiService webUiService;

    @Override
    public void run() {
        final WebUiService service = webUiService != null
                ? webUiService
                : Arc.container().instance(WebUiService.class).orElse(new WebUiService());
        service.launch();
    }
}
