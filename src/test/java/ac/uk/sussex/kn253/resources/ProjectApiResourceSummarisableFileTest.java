package ac.uk.sussex.kn253.resources;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SummaryResourceSummarisableFileTest {

    @Test
    void benchmarkShellScriptIsSummarisable() throws Exception {
        final Path script = Files.createTempFile("benchmark", ".sh");
        try {
            Files.writeString(script, "#!/usr/bin/env bash\necho ok\n", StandardCharsets.UTF_8);

            final SummaryResource resource = new SummaryResource();
            final Method method = SummaryResource.class.getDeclaredMethod("isSummarisableFile", Path.class);
            method.setAccessible(true);

            final boolean summarisable = (boolean) method.invoke(resource, script);
            assertTrue(summarisable, "Expected benchmark.sh-like scripts to be included in folder summaries");
        } finally {
            Files.deleteIfExists(script);
        }
    }
}
