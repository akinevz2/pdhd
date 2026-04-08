package ac.uk.sussex.kn253;

import ac.uk.sussex.kn253.commands.OllamaConfigCommand;
import ac.uk.sussex.kn253.commands.WebUiCommand;
import ac.uk.sussex.kn253.services.OllamaStartupCoordinator;
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

    @Inject
    OllamaStartupCoordinator ollamaStartupCoordinator;

    private CommandLine commandLine;

    public static void main(final String[] args) {
        Quarkus.run(PdhdLauncher.class, args);
    }

    @Override
    public void run() {
        Quarkus.waitForExit();
    }

    @Override
    public int run(final String... args) {
        final String[] effectiveArgs = args == null ? new String[0] : args;
        final String[] commandArgs = effectiveArgs.length == 0 ? new String[] { "webui" } : effectiveArgs;

        if (!isHelpOrVersion(commandArgs[0])) {
            try {
                ollamaStartupCoordinator.prepare(commandArgs[0]);
            } catch (final RuntimeException e) {
                System.err.println(e.getMessage());
                return CommandLine.ExitCode.SOFTWARE;
            }
        }

        final int exitCode = execute(commandArgs);
        if (exitCode == 0 && "webui".equals(commandArgs[0])) {
            Quarkus.waitForExit();
        }
        return exitCode;
    }

    private boolean isHelpOrVersion(final String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        return "-h".equals(command)
                || "--help".equals(command)
                || "-V".equals(command)
                || "--version".equals(command)
                || "help".equalsIgnoreCase(command);
    }

    int execute(final String... args) {
        if (commandLine == null) {
            commandLine = new CommandLine(this, factory);
        }
        return commandLine.execute(args);
    }
}
