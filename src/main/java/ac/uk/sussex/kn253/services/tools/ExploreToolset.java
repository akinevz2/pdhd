package ac.uk.sussex.kn253.services.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ac.uk.sussex.kn253.model.Project;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.*;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExploreToolset implements ToolProvider, ToolExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ToolSpecification> toolSpecifications;

    public ExploreToolset() {
        super();
        this.toolSpecifications = List.of(
                getCwdSpec(),
                resolvePathSpec(),
                pathInfoSpec(),
                listFoldersSpec(),
                listGitProjectsSpec(),
                listGithubProjectsSpec(),
                listFilesInProjectSpec());
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    public boolean canHandle(final String toolName) {
        return toolSpecifications.stream().anyMatch(spec -> spec.name().equals(toolName));
    }

    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest arg0) {
        final ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (final ToolSpecification spec : toolSpecifications) {
            builder.add(spec, this);
        }
        return builder.build();
    }

    @Override
    public String execute(final ToolExecutionRequest request, final Object memoryId) {
        try {
            final Map<String, Object> args = parseArgs(request.arguments());
            return switch (request.name()) {
                case "get_cwd" -> getCwd();
                case "resolve_path" -> resolvePath(args);
                case "path_info" -> pathInfo(args);
                case "list_folders" -> listFolders(args);
                case "list_git_projects" -> listGitProjects();
                case "list_github_projects" -> listGithubProjects();
                case "list_files_in_project" -> listFilesInProject(args);
                default -> "Unknown tool: " + request.name();
            };
        } catch (final IllegalArgumentException e) {
            return "Invalid tool arguments: " + e.getMessage();
        } catch (final Exception e) {
            return "Tool execution failed for " + request.name() + ": " + e.getMessage();
        }
    }

    private ToolSpecification getCwdSpec() {
        return ToolSpecification.builder()
                .name("get_cwd")
                .description("Return the current working directory as an absolute path.")
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    private ToolSpecification resolvePathSpec() {
        return ToolSpecification.builder()
                .name("resolve_path")
                .description(
                        "Resolve an absolute or relative path against cwd and return the normalized absolute path.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("Absolute or relative path").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification pathInfoSpec() {
        return ToolSpecification.builder()
                .name("path_info")
                .description(
                        "Return basic metadata for a path (exists, type, readability, writability, absolute path).")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("Absolute or relative path").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification listFoldersSpec() {
        return ToolSpecification.builder()
                .name("list_folders")
                .description("List immediate sub-folders for a given absolute or relative path.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("path",
                                JsonStringSchema.builder().description("Directory path to inspect").build())
                        .required("path")
                        .build())
                .build();
    }

    private ToolSpecification listGitProjectsSpec() {
        return ToolSpecification.builder()
                .name("list_git_projects")
                .description("List known projects in the database that have a Git repository attached.")
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    private ToolSpecification listGithubProjectsSpec() {
        return ToolSpecification.builder()
                .name("list_github_projects")
                .description("List known projects in the database that have GitHub repository metadata attached.")
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    private ToolSpecification listFilesInProjectSpec() {
        return ToolSpecification.builder()
                .name("list_files_in_project")
                .description("List files and folders in a project's directory, optionally under a relative subpath.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("projectDirectory",
                                JsonStringSchema.builder().description("Absolute path to the project root directory")
                                        .build())
                        .addProperty("relativePath",
                                JsonStringSchema.builder().description("Optional sub-directory inside the project")
                                        .build())
                        .required("projectDirectory")
                        .build())
                .build();
    }

    private Map<String, Object> parseArgs(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (final Exception e) {
            return Map.of();
        }
    }

    private String listFolders(final Map<String, Object> args) {
        final Path path = Path.of(getString(args, "path", ".")).normalize();
        if (!Files.isDirectory(path)) {
            return "Not a directory: " + path;
        }
        try {
            final List<String> folders = Files.list(path)
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
            if (folders.isEmpty()) {
                return "No folders found in " + path;
            }
            return String.join("\n", folders);
        } catch (final IOException e) {
            return "Failed to list folders for " + path + ": " + e.getMessage();
        }
    }

    private String getCwd() {
        return Path.of("").toAbsolutePath().normalize().toString();
    }

    private String resolvePath(final Map<String, Object> args) {
        final Path resolved = Path.of(require(args, "path")).toAbsolutePath().normalize();
        return resolved.toString();
    }

    private String pathInfo(final Map<String, Object> args) {
        final Path target = Path.of(require(args, "path")).toAbsolutePath().normalize();
        final boolean exists = Files.exists(target);
        final String type;
        if (!exists) {
            type = "missing";
        } else if (Files.isDirectory(target)) {
            type = "directory";
        } else if (Files.isRegularFile(target)) {
            type = "file";
        } else {
            type = "other";
        }

        return "path=" + target + "\n"
                + "exists=" + exists + "\n"
                + "type=" + type + "\n"
                + "readable=" + Files.isReadable(target) + "\n"
                + "writable=" + Files.isWritable(target);
    }

    private String listGitProjects() {
        final List<Project> projects = Project.<Project>listAll().stream()
                .filter(p -> p.getGitRepository() != null)
                .sorted(Comparator.comparing(Project::getDirectory, Comparator.nullsLast(String::compareTo)))
                .toList();
        if (projects.isEmpty()) {
            return "No git projects found in database.";
        }
        return projects.stream()
                .map(p -> "- " + p.getDirectory())
                .collect(Collectors.joining("\n"));
    }

    private String listGithubProjects() {
        final List<Project> projects = Project.<Project>listAll().stream()
                .filter(p -> p.getGithubRepository() != null)
                .sorted(Comparator.comparing(Project::getDirectory, Comparator.nullsLast(String::compareTo)))
                .toList();
        if (projects.isEmpty()) {
            return "No GitHub projects found in database.";
        }
        return projects.stream()
                .map(p -> "- " + p.getDirectory() + " -> " + p.getGithubRepository().getName())
                .collect(Collectors.joining("\n"));
    }

    private String listFilesInProject(final Map<String, Object> args) {
        final Path project = Path.of(require(args, "projectDirectory")).normalize();
        final String relativePath = getString(args, "relativePath", "");
        final Path target = relativePath.isBlank() ? project : project.resolve(relativePath).normalize();
        if (!target.startsWith(project)) {
            return "Invalid relativePath: outside project directory.";
        }
        if (!Files.isDirectory(target)) {
            return "Not a directory: " + target;
        }
        try {
            final List<String> entries = Files.list(target)
                    .sorted()
                    .map(p -> {
                        final String name = p.getFileName().toString();
                        return Files.isDirectory(p) ? name + "/" : name;
                    })
                    .toList();
            if (entries.isEmpty()) {
                return "No entries found in " + target;
            }
            return String.join("\n", entries);
        } catch (final IOException e) {
            return "Failed to list files for " + target + ": " + e.getMessage();
        }
    }

    private String require(final Map<String, Object> args, final String key) {
        final String val = getString(args, key, "");
        if (val.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return val;
    }

    private String getString(final Map<String, Object> args, final String key, final String defaultValue) {
        final Object val = args.get(key);
        return val == null ? defaultValue : String.valueOf(val);
    }

}
