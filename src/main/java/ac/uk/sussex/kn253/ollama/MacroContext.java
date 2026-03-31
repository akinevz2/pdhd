package ac.uk.sussex.kn253.ollama;

import java.nio.file.Path;

import ac.uk.sussex.kn253.services.WorkingDirectoryService;

public record MacroContext(Path cwd) {

    public MacroContext(final WorkingDirectoryService workingDirectoryService) {
        this(workingDirectoryService.getCurrentWorkingDirectory());
    }

    public MacroContext(final String string) {
        this(Path.of(string));
    }

}
