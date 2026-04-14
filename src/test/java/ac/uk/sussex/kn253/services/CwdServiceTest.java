package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CwdServiceTest {

    @Test
    void getCurrentWorkingDirectoryReturnsProcessUserDirPath() {
        final CwdService service = new CwdService();
        final Path resolved = service.getCurrentWorkingDirectory();

        assertEquals(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
                resolved.toAbsolutePath().normalize());
    }
}