package ac.uk.sussex.kn253.services.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import ac.uk.sussex.kn253.services.tools.macro.read.ReadToolSupport;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

class MacroToolModuleIntrospectTest {

        @Test
        void readToolSupportSkipsCachingOutsideQuarkus(@TempDir final Path tempDir) {
                final ReadToolSupport support = new ReadToolSupport();

                assertThrows(UnsupportedOperationException.class,
                                () -> support.cacheFolderManifest(tempDir, tempDir.resolve("docs"), "manifest"),
                                "Caching should throw UnsupportedOperationException when Panache is unavailable");
        }

        @Test
        void readFolderManifestSummarizesOnlyTargetFolderTree(@TempDir final Path tempDir) throws Exception {
                final Path folder = tempDir.resolve("workspace/docs");
                Files.createDirectories(folder.resolve("guides"));
                Files.writeString(folder.resolve("README.md"), "# Docs\n");
                Files.writeString(folder.resolve("guides/intro.md"), "Welcome\n");
                Files.writeString(folder.resolve("notes.txt"), "Evidence\n");

                final Path outsideFolder = tempDir.resolve("workspace/src/main/java");
                Files.createDirectories(outsideFolder);
                Files.writeString(outsideFolder.resolve("App.java"), "class App {}\n");

                final WorkingDirectoryService cwd = new WorkingDirectoryService();
                cwd.navigateTo(tempDir.resolve("workspace").toString());
                final MacroToolModule toolset = new MacroToolModule(cwd);

                final String result = toolset.execute(
                                request("read_folder_manifest", "{\"path\":\"docs\"}"),
                                null);

                assertTrue(result.contains("=== folder entries (recursive) ==="));
                assertTrue(result.contains("README.md"));
                assertTrue(result.contains("guides/"));
                assertTrue(result.contains("guides/intro.md"));
                assertTrue(result.contains("=== sampled file contents (evidence only) ==="));
                assertTrue(result.contains("# Docs"));
                assertFalse(result.contains("class App"), "Content outside target folder should not be included");
                assertTrue(result.contains("content is unknown unless read via read_file"));
        }

        @Test
        void readProjectManifestIncludesRecursiveSrcListingAndContent(@TempDir final Path tempDir) throws Exception {
                Files.writeString(tempDir.resolve("README.md"), "# Demo Project\n");

                final Path srcMainJava = tempDir.resolve("src/main/java/ac/demo");
                Files.createDirectories(srcMainJava);
                Files.writeString(srcMainJava.resolve("App.java"),
                                "package ac.demo;\npublic class App { public static void main(String[] args) {} }\n");

                final Path srcTestJava = tempDir.resolve("src/test/java/ac/demo");
                Files.createDirectories(srcTestJava);
                Files.writeString(srcTestJava.resolve("AppTest.java"),
                                "package ac.demo;\nclass AppTest {}\n");

                final WorkingDirectoryService cwd = new WorkingDirectoryService();
                cwd.navigateTo(tempDir.toString());
                final MacroToolModule toolset = new MacroToolModule(cwd);

                final String result = toolset.execute(
                                request("read_project_manifest", "{\"path\":\".\"}"),
                                null);

                assertTrue(result.contains("=== README.md ==="));
                assertTrue(result.contains("=== src/ (recursive) ==="));
                assertTrue(result.contains("main/java/ac/demo/App.java"));
                assertTrue(result.contains("test/java/ac/demo/AppTest.java"));
                assertTrue(result.contains("=== src/ sampled file contents (evidence only) ==="));
                assertTrue(result.contains("Only files listed in this section were read for content"));
                assertTrue(result.contains("class App"));
        }

        @Test
        void readProjectManifestWithoutSrcDoesNotFail(@TempDir final Path tempDir) throws Exception {
                Files.writeString(tempDir.resolve("README.md"), "# Project Without Src\n");

                final WorkingDirectoryService cwd = new WorkingDirectoryService();
                cwd.navigateTo(tempDir.toString());
                final MacroToolModule toolset = new MacroToolModule(cwd);

                final String result = toolset.execute(
                                request("read_project_manifest", "{\"path\":\".\"}"),
                                null);

                assertTrue(result.contains("=== README.md ==="));
                assertTrue(!result.contains("=== src/ (recursive) ==="));
        }

        @Test
        void readProjectManifestReportsOmittedFilesAndAvoidsUnsampledContentClaims(@TempDir final Path tempDir)
                        throws Exception {
                final Path srcMainJava = tempDir.resolve("src/main/java/ac/demo");
                Files.createDirectories(srcMainJava);

                for (int i = 0; i < 45; i++) {
                        final String className = String.format("C%02d", i);
                        final String uniqueToken = "TOKEN_" + i;
                        Files.writeString(
                                        srcMainJava.resolve(className + ".java"),
                                        "package ac.demo; class " + className + " { String v = \"" + uniqueToken
                                                        + "\"; }\n");
                }

                final WorkingDirectoryService cwd = new WorkingDirectoryService();
                cwd.navigateTo(tempDir.toString());
                final MacroToolModule toolset = new MacroToolModule(cwd);

                final String result = toolset.execute(
                                request("read_project_manifest", "{\"path\":\".\"}"),
                                null);

                assertTrue(result.contains("=== src/ (recursive) ==="));
                assertTrue(result.contains("Content omitted for 5 src file(s)."));
                assertTrue(result.contains("C44.java"),
                                "Unsampled files should still be discovered in recursive listing");
                assertFalse(result.contains("TOKEN_44"),
                                "Unsampled file content should not be claimed in sampled-content section");
                assertTrue(result.contains("content is unknown unless read via read_file"));
        }

        private ToolExecutionRequest request(final String name, final String jsonArguments) {
                return ToolExecutionRequest.builder().name(name).arguments(jsonArguments).build();
        }
}
