package ac.uk.sussex.kn253.services.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ac.uk.sussex.kn253.model.*;
import ac.uk.sussex.kn253.services.WorkingDirectoryService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class ExploreToolsetTest {

    @Inject
    ExploreToolset cdiToolset;

    @Inject
    WorkingDirectoryService workingDirectoryService;

    private ExploreToolset toolset;

    @BeforeEach
    @Transactional
    void clearDatabase() {
        toolset = new ExploreToolset();
        ac.uk.sussex.kn253.model.ProjectKnowledge.deleteAll();
        Project.deleteAll();
        GitRepository.deleteAll();
        GithubRepository.deleteAll();
    }

    @Test
    void getCwdReturnsAbsolutePath() {
        final String result = toolset.execute(request("get_current_working_directory", "{}"), null);
        assertTrue(Path.of(result).isAbsolute());
    }

    @Test
    void resolvePathReturnsNormalizedAbsolutePath() {
        final String cwd = toolset.execute(request("get_current_working_directory", "{}"), null);
        final String result = toolset.execute(request("resolve_path", "{\"path\":\".\"}"), null);
        assertEquals(Path.of(cwd).toAbsolutePath().normalize().toString(), result);
    }

    @Test
    void searchPathsFindsExactPrefixAndSubstringMatches(@TempDir final Path tempDir) throws Exception {
        final Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve("src/main/webui"));
        Files.createDirectories(workspace.resolve("src/frontend-tests"));
        Files.writeString(workspace.resolve("src/main/webui/config.json"), "{}\n");
        Files.writeString(workspace.resolve("src/main/webui/WebView.tsx"), "export const WebView = () => null;\n");

        final WorkingDirectoryService cwd = new WorkingDirectoryService();
        cwd.navigateTo(workspace.toString());
        final ExploreToolset searchToolset = new ExploreToolset(cwd);

        final String webuiResult = searchToolset.execute(
                request("search_paths", "{\"query\":\"webui\"}"),
                null);
        assertTrue(webuiResult.contains("match=exact relative=src/main/webui"));
        assertTrue(webuiResult.contains("type=directory"));

        final String frontResult = searchToolset.execute(
                request("search_paths", "{\"query\":\"front\"}"),
                null);
        assertTrue(frontResult.contains("match=prefix relative=src/frontend-tests"));

        final String viewResult = searchToolset.execute(
                request("search_paths", "{\"query\":\"view\"}"),
                null);
        assertTrue(viewResult.contains("match=substring relative=src/main/webui/WebView.tsx"));
    }

    @Test
    void searchPathsCanRestrictToDirectoriesAndReportNoMatches(@TempDir final Path tempDir) throws Exception {
        final Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve("config"));
        Files.writeString(workspace.resolve("config/app-config.yaml"), "name: demo\n");

        final WorkingDirectoryService cwd = new WorkingDirectoryService();
        cwd.navigateTo(workspace.toString());
        final ExploreToolset searchToolset = new ExploreToolset(cwd);

        final String directoriesOnly = searchToolset.execute(
                request("search_paths", "{\"query\":\"config\",\"includeFiles\":false}"),
                null);
        assertTrue(directoriesOnly.contains("type=directory match=exact relative=config"));
        assertFalse(directoriesOnly.contains("app-config.yaml"));

        final String noMatch = searchToolset.execute(
                request("search_paths", "{\"query\":\"missing-target\"}"),
                null);
        assertTrue(noMatch.contains("matches=0"));
        assertTrue(noMatch.contains("No matching paths found."));
    }

    @Test
    @Transactional
    void listGitProjectsTriggersDiscoveryIndexingFromCurrentFolder(@TempDir final Path tempDir) throws Exception {
        final Path workspace = tempDir.resolve("workspace");
        final Path repo = workspace.resolve("demo-repo");
        Files.createDirectories(repo);

        runCommand(repo, "git", "init");
        runCommand(repo, "git", "config", "user.name", "PDHD Test");
        runCommand(repo, "git", "config", "user.email", "pdhd-test@example.com");
        Files.writeString(repo.resolve("README.md"), "# Demo\n");
        runCommand(repo, "git", "add", "README.md");
        runCommand(repo, "git", "commit", "-m", "initial");

        workingDirectoryService.navigateTo(workspace.toString());

        assertNull(Project.find("directory", repo.toAbsolutePath().normalize().toString()).firstResult());

        final String result = cdiToolset.execute(request("list_git_projects", "{}"), null);
        assertTrue(result.contains(repo.toAbsolutePath().normalize().toString()));

        final Project indexed = Project.find("directory", repo.toAbsolutePath().normalize().toString())
                .firstResult();
        assertNotNull(indexed, "Expected list_git_projects to trigger discovery indexing from cwd");
    }

    @Test
    void pathInfoDescribesPath(@TempDir final Path tempDir) throws Exception {
        final Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "hello");

        final String result = toolset.execute(
                request("get_path_info", "{\"path\":\"" + escape(file) + "\"}"),
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
                request("list_subdirectories", "{\"path\":\"" + escape(tempDir) + "\"}"),
                null);

        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertFalse(result.contains("plain.txt"));
    }

    @Test
    void navigateToolChangesWorkingDirectory(@TempDir final Path tempDir) throws Exception {
        final Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve("src"));

        final String navigate = toolset.execute(
                request("change_working_directory", "{\"path\":\"" + escape(workspace) + "\"}"),
                null);
        assertTrue(navigate.contains("cwd=" + workspace.toAbsolutePath().normalize()));

        final String cwd = toolset.execute(request("get_current_working_directory", "{}"), null);
        assertEquals(workspace.toAbsolutePath().normalize().toString(), cwd);

        final String listResult = toolset.execute(request("list_subdirectories", "{}"), null);
        assertTrue(listResult.contains("path=" + workspace.toAbsolutePath().normalize()));
        assertTrue(listResult.contains("src"));
    }

    @Test
    void navigateToolSupportsGoingUpOneFolder(@TempDir final Path tempDir) throws Exception {
        final Path level1 = tempDir.resolve("level1");
        final Path level2 = level1.resolve("level2");
        Files.createDirectories(level2);

        final String enterChild = toolset.execute(
                request("change_working_directory", "{\"path\":\"" + escape(level2) + "\"}"),
                null);
        assertTrue(enterChild.contains("cwd=" + level2.toAbsolutePath().normalize()));

        final String upOne = toolset.execute(request("change_working_directory", "{\"path\":\"..\"}"), null);
        assertTrue(upOne.contains("cwd=" + level1.toAbsolutePath().normalize()));

        final String cwd = toolset.execute(request("get_current_working_directory", "{}"), null);
        assertEquals(level1.toAbsolutePath().normalize().toString(), cwd);
    }

    @Test
    void navigateToolSupportsGoingUpTwoFolders(@TempDir final Path tempDir) throws Exception {
        final Path level1 = tempDir.resolve("level1");
        final Path level2 = level1.resolve("level2");
        final Path level3 = level2.resolve("level3");
        Files.createDirectories(level3);

        final String enterDeep = toolset.execute(
                request("change_working_directory", "{\"path\":\"" + escape(level3) + "\"}"),
                null);
        assertTrue(enterDeep.contains("cwd=" + level3.toAbsolutePath().normalize()));

        final String upTwo = toolset.execute(request("change_working_directory", "{\"path\":\"../..\"}"), null);
        assertTrue(upTwo.contains("cwd=" + level1.toAbsolutePath().normalize()));

        final String cwd = toolset.execute(request("get_current_working_directory", "{}"), null);
        assertEquals(level1.toAbsolutePath().normalize().toString(), cwd);
    }

    @Test
    void listFolderReturnsAllFilesRecursively(@TempDir final Path tempDir) throws Exception {
        final Path src = tempDir.resolve("src");
        Files.createDirectories(src.resolve("nested"));
        Files.writeString(src.resolve("Main.java"), "class Main {}\n");
        Files.writeString(src.resolve("nested/Helper.java"), "class Helper {}\n");

        final String result = toolset.execute(
                request("list_files_recursive", "{\"path\":\"" + escape(src) + "\"}"),
                null);

        assertTrue(result.contains("Main.java"));
        assertTrue(result.contains("nested/Helper.java"));
    }

    @Test
    void explainToolAnalyzesFile(@TempDir final Path tempDir) throws Exception {
        final Path file = tempDir.resolve("notes.md");
        Files.writeString(file, "# Title\n\nSome content here.\n");

        final String result = toolset.execute(
                request("analyze_path_detailed", "{\"path\":\"" + escape(file) + "\"}"),
                null);

        assertTrue(result.contains("Detailed file analysis"));
        assertTrue(result.contains("extension=md"));
        assertTrue(result.contains("contentPreview="));
    }

    @Test
    void summariseToolAnalyzesDirectory(@TempDir final Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("pkg"));
        Files.writeString(tempDir.resolve("Main.java"), "class Main {}\n");
        Files.writeString(tempDir.resolve("pkg/Helper.java"), "class Helper {}\n");
        Files.writeString(tempDir.resolve("README.md"), "hello\n");

        final String result = toolset.execute(
                request("summarize_path", "{\"path\":\"" + escape(tempDir) + "\"}"),
                null);

        assertTrue(result.contains("Directory summary"));
        assertTrue(result.contains("files=3"));
        assertTrue(result.contains("extensions="));
    }

    @Test
    void listFilesInProjectHandlesRelativePath(@TempDir final Path tempDir) throws Exception {
        final Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.java"), "class Main {}\n");
        Files.createDirectories(src.resolve("nested"));

        final String args = "{\"projectDirectory\":\"" + escape(tempDir)
                + "\",\"relativePath\":\"src\"}";
        final String result = toolset.execute(request("list_project_entries", args), null);

        assertTrue(result.contains("Main.java"));
        assertTrue(result.contains("nested/"));
    }

    @Test
    @Transactional
    void listGitProjectsReturnsProjectsWithGitRepository() throws Exception {
        final GitRepository git = new GitRepository(
                null,
                java.util.List.of(new Origin("origin", URI.create("https://example.com/r.git").toURL())));
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
    void getGitLogReturnsRecentCommits(@TempDir final Path tempDir) throws Exception {
        runCommand(tempDir, "git", "init");
        runCommand(tempDir, "git", "config", "user.name", "PDHD Test");
        runCommand(tempDir, "git", "config", "user.email", "pdhd-test@example.com");

        final Path readme = tempDir.resolve("README.md");
        Files.writeString(readme, "# Demo\n");
        runCommand(tempDir, "git", "add", "README.md");
        runCommand(tempDir, "git", "commit", "-m", "initial commit");

        final String args = "{\"path\":\"" + escape(tempDir) + "\",\"maxCount\":5}";
        final String result = toolset.execute(request("get_git_log", args), null);

        assertTrue(result.contains("path=" + tempDir.toAbsolutePath().normalize()));
        assertTrue(result.toLowerCase().contains("initial commit"));
    }

    @Test
    void getGitLogReturnsHelpfulErrorForNonRepository(@TempDir final Path tempDir) {
        final String args = "{\"path\":\"" + escape(tempDir) + "\"}";
        final String result = toolset.execute(request("get_git_log", args), null);

        assertTrue(result.contains("Failed to get git log"));
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

    private static void runCommand(final Path cwd, final String... command) throws Exception {
        final Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .start();
        final int exit = process.waitFor();
        if (exit != 0) {
            final String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + stderr);
        }
    }
}
