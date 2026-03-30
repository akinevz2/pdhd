package ac.uk.sussex.kn253.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import ac.uk.sussex.kn253.services.tools.ToolModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

class ToolServiceTest {

    @Test
    void rejectsDuplicateToolNamesAcrossModules() {
        final ToolModule first = new TestModule("mod-a", "shared_tool", "ok");
        final ToolModule second = new TestModule("mod-b", "shared_tool", "ok");

        final ToolService service = new ToolService(List.of(first, second), new ToolTelemetryService());

        assertThrows(IllegalStateException.class, service::toolSpecifications);
    }

    @Test
    void recordsValidationFailuresInTelemetry() {
        final ToolTelemetryService telemetry = new ToolTelemetryService();
        final ToolModule module = new TestModule("mod", "read_file", "Invalid tool arguments: missing field");
        final ToolService service = new ToolService(List.of(module), telemetry);

        final String result = service.execute(
                ToolExecutionRequest.builder().name("read_file").arguments("{}").build(),
                null);

        assertEquals("Invalid tool arguments: missing field", result);
        final var snapshot = telemetry.snapshot().stream().filter(s -> "read_file".equals(s.toolName())).findFirst()
                .orElseThrow();
        assertEquals(1L, snapshot.invocations());
        assertEquals(1L, snapshot.failures());
        assertEquals(1L, snapshot.argumentValidationFailures());
        assertEquals(1L, snapshot.errorClasses().getOrDefault("ArgumentValidation", 0L));
    }

    private static final class TestModule implements ToolModule {

        private final String moduleName;
        private final String toolName;
        private final String result;

        private TestModule(final String moduleName, final String toolName, final String result) {
            this.moduleName = moduleName;
            this.toolName = toolName;
            this.result = result;
        }

        @Override
        public List<ToolSpecification> toolSpecifications() {
            return List.of(ToolSpecification.builder().name(toolName).description(moduleName).build());
        }

        @Override
        public boolean canHandle(final String requestedToolName) {
            return toolName.equals(requestedToolName);
        }

        @Override
        public String execute(final ToolExecutionRequest request, final Object memoryId) {
            return result;
        }
    }
}
