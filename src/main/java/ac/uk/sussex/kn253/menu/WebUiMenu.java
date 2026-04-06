package ac.uk.sussex.kn253.menu;

import ac.uk.sussex.kn253.services.WebUiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;

@ApplicationScoped
@Command(name = "webui", description = "Launch web UI")
public class WebUiMenu implements Runnable {

    private final WebUiService webUiService;

    @Inject
    WebUiMenu(final WebUiService webUiService) {
        this.webUiService = webUiService;
    }

    @Override
    public void run() {
        webUiService.launch();
    }
}
