package ac.uk.sussex.kn253.services.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ac.uk.sussex.kn253.model.GitRepository;
import ac.uk.sussex.kn253.model.GithubRepository;
import ac.uk.sussex.kn253.model.Origin;
import ac.uk.sussex.kn253.model.Project;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;

@QuarkusTest
class ExploreToolsetTest {

    private final ExploreToolset toolset = new ExploreToolset();

    @BeforeEach
    @Transactional
    void clearDatabase() {
        Project.deleteAll();
        GitRepository.deleteAll();
        GithubRepository.deleteAll();
    }

    @Test
    void getCwdReturnsAbsolutePath() {
        final String result = toolset.execute(request("get_cwd", "{}"), null);
        assertTrue(Path.of(result).isAbsolute());
    }

    @Test
    void resolvePathReturnsNormalizedAbsolutePath() {
        final String result = toolset.execute(request("resolve_path", "{\"path\":\".\"}"), null);
        assertEquals(Path.of(".").toAbsolutePath().normalize().toString(), result);
    }

    @Test
    void pathInfoDescribesPath(@TempDir final Path tempDir) throws Exception {
        final Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "hello");

        final String result = toolset.execute(
                request("path_info", "{\"path\":\"" + escape(file) + "\"}"),
                null);

        assertTrue(result.contains("exists=true"));
        assertTrue(result.contains("type=file"));
        assertTrue(result.contains("readable=true"));
    }

    @Test
    void listFoldersReturnsOnlyDirectories(@TempDir final Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("a"));
        Files.createDirectories(tempDir.resolve("b"));
        Files.writeString(tempDir.resolve("plain.txt"), "x");

        final String result = toolset.execute(
                request("list_folders", "{\"path\":\"" + escape(tempDir) + "\"}"),
                null);

        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertFalse(result.contains("plain.txt"));
    }

    @Test
    void listFilesInProjectHandlesRelativePath(@TempDir final Path tempDir) throws Exception {
        final Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.java"), "class Main {}\n");
        Files.createDirectories(src.resolve("nested"));

        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"relativePath\":\"src\"}";
        final String result = toolset.execute(request("list_files_in_project", args), null);

        assertTrue(result.contains("Main.java"));
        assertTrue(result.contains("nested/"));
    }

    @Test
    @Transactional
    void listGitProjectsReturnsProjectsWithGitRepository() throws Exception {
        final GitRepository git = new GitRepository(
                null,
                java.util.List.of(new Origin("origin", new URL("https://example.com/r.git"))));
        git.persist();

        final Project withGit = new Project(null, "/tmp/with-git", null, git);
        withGit.persist();

        final Project withoutGit = new Project(null, "/tmp/without-git", null, null);
        withoutGit.persist();

        final String result = toolset.execute(request("list_git_projects", "{}"), null);
        assertTrue(result.contains("/tmp/with-git"));
        assertFalse(result.contains("/tmp/without-git"));
    }

    @Test
    @Transactional
    void listGithubProjectsReturnsProjectsWithGithubMetadata() {
        final GithubRepository github = new GithubRepository(null, "demo-repo", "desc");
        github.persist();

        final Project withGithub = new Project(null, "/tmp/with-github", github, null);
        withGithub.persist();

        final String result = toolset.execute(request("list_github_projects", "{}"), null);
        assertTrue(result.contains("/tmp/with-github"));
        assertTrue(result.contains("demo-repo"));
    }

    @Test
    void unknownToolReturnsHelpfulMessage() {
        final String result = toolset.execute(request("not_a_tool", "{}"), null);
        assertTrue(result.contains("Unknown tool"));
    }

    private ToolExecutionRequest request(final String name, final String jsonArguments) {
        return ToolExecutionRequest.builder()
                .name(name)
                .arguments(jsonArguments)
                .build();
    }

    private String escape(final Path path) {
        return path.toString().replace("\\", "\\\\");
    }
}
