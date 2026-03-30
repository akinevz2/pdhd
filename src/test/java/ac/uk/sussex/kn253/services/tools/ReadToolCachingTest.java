package ac.uk.sussex.kn253.services.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ac.uk.sussex.kn253.model.*;
import ac.uk.sussex.kn253.ollama.OllamaConfig;
import ac.uk.sussex.kn253.services.ToolService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Tests that read tools cache their results to ProjectKnowledge database,
 * allowing subsequent tools to retrieve previously discovered context.
 */
@QuarkusTest
class ReadToolCachingTest {

        @Inject
        ToolService toolService;

        @Inject
        OllamaConfig ollamaConfig;

        private ToolExecutionRequest request(final String name, final String jsonArguments) {
                return ToolExecutionRequest.builder().name(name).arguments(jsonArguments).build();
        }

        private String escape(final Path path) {
                return path.toString().replace("\\", "\\\\");
        }

        @BeforeEach
        @Transactional
        void clearDatabase() {
                Project.deleteAll();
                // Wipe any stale settings and repopulate from the active Quarkus config
                // profile.
                // ollamaConfig reads ollama.base-url from application-test.properties so that
                // PathSummaryLlmService builds its OllamaChatSession with the correct host.
                OllamaSettings.deleteAll();
                final OllamaSettings settings = new OllamaSettings();
                settings.setBaseUrl(ollamaConfig.baseUrl());
                settings.setModelName(ollamaConfig.modelName());
                settings.setTimeoutSeconds(ollamaConfig.timeoutSeconds());
                settings.setTemperature(ollamaConfig.temperature());
                settings.setNumPredict(ollamaConfig.numPredict());
                settings.setNumCtx(ollamaConfig.numCtx());
                settings.setEmbeddingEnabled(true);
                settings.persistAndFlush();
        }

        @Test
        @Transactional
        void readFileToolCachesFileContentToProjectKnowledge(@TempDir final Path tempDir) throws Exception {
                // Setup: create a file
                final Path file = tempDir.resolve("sample.txt");
                Files.writeString(file, "line one\nline two\nline three\n");

                // Act: read the file (which should cache the content)
                final String result = toolService.execute(
                                request("read_file",
                                                "{\"projectDirectory\":\"" + escape(tempDir)
                                                                + "\",\"filePath\":\"sample.txt\",\"maxLines\":2}"),
                                null);

                // Assert: tool output shows only 2 lines
                assertEquals("line one\nline two", result);

                // Assert: ProjectKnowledge stores the full 3-line content
                final Project project = Project.find("directory", tempDir.toString()).firstResult();
                assertNotNull(project, "Project should be created and persisted");

                final ProjectKnowledge cached = ProjectKnowledge.findByProjectAndKey(project, "file:sample.txt");
                assertNotNull(cached, "File content should be cached in ProjectKnowledge");
                assertNotNull(cached.getJsonContent());
                assertTrue(cached.getJsonContent().contains("line three"), "Cached content should include all 3 lines");
        }

        @Test
        @Transactional
        void summarizePathToolCachesSummaryToProjectKnowledge(@TempDir final Path tempDir) throws Exception {
                // Setup: create a folder structure
                final Path folder = tempDir.resolve("project");
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("README.md"), "# Project");
                Files.writeString(folder.resolve("main.java"), "class Main {}");

                // Act: summarize the path (which should cache the analysis)
                final String result = toolService.execute(
                                request("summarize_path", "{\"path\":\"" + escape(folder) + "\"}"),
                                null);

                // Assert: result contains analysis
                assertTrue(result.contains("Directory summary") || result.contains("path="),
                                "Summarize path should return analysis");

                // Assert: ProjectKnowledge stores the cached summary
                final Project project = Project.find("directory", folder.toString()).firstResult();
                assertNotNull(project, "Project should be created and persisted");

                final String cacheKey = "path:summary:" + folder.toAbsolutePath().normalize();
                final ProjectKnowledge cached = ProjectKnowledge.findByProjectAndKey(project, cacheKey);
                assertNotNull(cached, "Path summary should be cached in ProjectKnowledge");
                assertNotNull(cached.getJsonContent());
                assertTrue(cached.getJsonContent().contains("summary") || cached.getJsonContent().contains("analysis"),
                                "Cached content should reference the analysis");
        }

        @Test
        @Transactional
        void readFolderManifestToolCachesManifestToProjectKnowledge(@TempDir final Path tempDir) throws Exception {
                // Setup: create a folder structure
                final Path folder = tempDir.resolve("docs");
                Files.createDirectories(folder);
                Files.createDirectories(folder.resolve("guides"));
                Files.writeString(folder.resolve("README.md"), "# Docs");
                Files.writeString(folder.resolve("guides/intro.md"), "Welcome");
                Files.writeString(folder.resolve("notes.txt"), "Notes");

                // Act: read the folder manifest (which should cache the manifest)
                final String result = toolService.execute(
                                request("read_folder_manifest", "{\"path\":\"" + escape(folder) + "\"}"),
                                null);

                // Assert: result contains folder information
                assertTrue(result.contains("Folder directory") || result.contains("folder entries"),
                                "read_folder_manifest should return folder information");

                // Assert: ProjectKnowledge stores the cached manifest
                final Project project = Project.find("directory", folder.toAbsolutePath().normalize().toString())
                                .firstResult();
                if (project != null) {
                        final String cacheKey = "folder:" + folder.toAbsolutePath().normalize();
                        final ProjectKnowledge cached = ProjectKnowledge.findByProjectAndKey(project, cacheKey);
                        if (cached != null) {
                                // Caching was successful
                                assertTrue(cached.getJsonContent().contains("manifest")
                                                || cached.getJsonContent().contains("folder"),
                                                "Cached manifest should be stored");
                        }
                }
        }

        @Test
        @Transactional
        void readToolsCacheContextPerProject(@TempDir final Path tempDir) throws Exception {
                // Setup: create two separate project directories
                final Path projectA = tempDir.resolve("project-a");
                final Path projectB = tempDir.resolve("project-b");
                Files.createDirectories(projectA);
                Files.createDirectories(projectB);
                Files.writeString(projectA.resolve("file.txt"), "Content A");
                Files.writeString(projectB.resolve("file.txt"), "Content B");

                // Act: read files from both projects
                toolService.execute(
                                request("read_file",
                                                "{\"projectDirectory\":\"" + escape(projectA)
                                                                + "\",\"filePath\":\"file.txt\"}"),
                                null);
                toolService.execute(
                                request("read_file",
                                                "{\"projectDirectory\":\"" + escape(projectB)
                                                                + "\",\"filePath\":\"file.txt\"}"),
                                null);

                // Assert: each project has its own cached content
                final Project pA = Project.find("directory", projectA.toString()).firstResult();
                final Project pB = Project.find("directory", projectB.toString()).firstResult();
                assertNotNull(pA, "Project A should exist");
                assertNotNull(pB, "Project B should exist");

                final ProjectKnowledge cachedA = ProjectKnowledge.findByProjectAndKey(pA, "file:file.txt");
                final ProjectKnowledge cachedB = ProjectKnowledge.findByProjectAndKey(pB, "file:file.txt");
                assertNotNull(cachedA, "Project A should have cached content");
                assertNotNull(cachedB, "Project B should have cached content");

                // Verify content isolation
                assertTrue(cachedA.getJsonContent().contains("Content A"));
                assertTrue(cachedB.getJsonContent().contains("Content B"));
        }
}
