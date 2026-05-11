package ac.uk.sussex.kn253;

import ac.uk.sussex.kn253.commands.OllamaConfigCommand;
import ac.uk.sussex.kn253.commands.WebUiCommand;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

@TopCommand
@Command(name = PdhdLauncher.NAME, description = PdhdLauncher.DESCRIPTION, mixinStandardHelpOptions = true, subcommands = {
        OllamaConfigCommand.class, WebUiCommand.class })
@ApplicationScoped
public class PdhdLauncher implements QuarkusApplication, Runnable {

    public static final String DESCRIPTION = "Project Definition Hierarchy Discovery";
    public static final String NAME = "pdhd";

    @Inject
    IFactory factory;

    private CommandLine commandLine;

    public static void main(final String[] args) {
        Quarkus.run(PdhdLauncher.class, args);
    }

    static boolean shouldRunBootstrap(final String[] args) {
        if (args == null || args.length == 0) {
            return true;
        }
        final String command = args[0];
        if (command == null) {
            return true;
        }
        final String normalized = command.trim();
        if (normalized.isEmpty()) {
            return true;
        }
        return !"configure".equalsIgnoreCase(normalized)
                && !"-h".equals(normalized)
                && !"--help".equals(normalized)
                && !"-V".equals(normalized)
                && !"--version".equals(normalized)
                && !"help".equalsIgnoreCase(normalized);
    }

    @Override
    public void run() {
        Quarkus.waitForExit();
    }

    @Override
    public int run(final String... args) {
        final String[] effectiveArgs = args == null ? new String[0] : args;
        final String[] commandArgs = effectiveArgs.length == 0 ? new String[] { "webui" } : effectiveArgs;
        final int exitCode = execute(commandArgs);
        if (exitCode == 0 && "webui".equals(commandArgs[0])) {
            Quarkus.waitForExit();
        }
        return exitCode;
    }

    int execute(final String... args) {
        if (commandLine == null) {
            commandLine = new CommandLine(this, factory);
        }
        return commandLine.execute(args);
    }
}
