package ac.uk.sussex.kn253.services.tools.macro.write;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ac.uk.sussex.kn253.model.Project;
import ac.uk.sussex.kn253.model.ProjectKnowledge;

public class WriteToolSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String writeFile(
            final Path output,
            final String content,
            final boolean append,
            final String prefix) {
        try {
            Files.createDirectories(output.getParent());
            if (append) {
                Files.writeString(output, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(output, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            return prefix + ": " + output;
        } catch (final IOException e) {
            return "Failed to write file " + output + ": " + e.getMessage();
        }
    }

    public List<String> toStringList(final Object value) {
        if (value instanceof final String rawString) {
            return rawString.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.replaceFirst("^[-*]\\s+", ""))
                    .map(line -> line.replaceFirst("^\\d+\\.\\s+", ""))
                    .toList();
        }
        if (!(value instanceof final List<?> raw)) {
            return List.of();
        }
        final List<String> out = new ArrayList<>(raw.size());
        for (final Object item : raw) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    public String slug(final String title) {
        final String normalized = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "untitled" : normalized;
    }

    public Project resolveOrCreateProject(final Path projectDirectory) {
        final String directory = projectDirectory.toString();
        final Project existing = Project.find("directory", directory).firstResult();
        if (existing != null) {
            return existing;
        }

        final Project created = new Project(null, directory, null, null);
        created.persist();
        return created;
    }

    public ObjectNode parseKnowledgeObject(
            final String rawJson,
            final String tag,
            final Path projectDirectory) {
        try {
            final JsonNode parsed = OBJECT_MAPPER.readTree(rawJson);
            if (parsed instanceof final ObjectNode objectNode) {
                return objectNode;
            }
        } catch (final IOException ignored) {
        }

        final ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("tag", tag);
        root.put("projectDirectory", projectDirectory.toString());
        root.putArray("entries");
        return root;
    }

    public String cacheProjectKnowledge(
            final Path projectDirectory,
            final String tag,
            final String note,
            final String query,
            final String source) {
        final Instant now = Instant.now();
        final Project project = resolveOrCreateProject(projectDirectory);
        ProjectKnowledge knowledge = ProjectKnowledge.findByProjectAndKey(project, tag);

        final ObjectNode root;
        final ArrayNode entries;
        if (knowledge == null || knowledge.getJsonContent() == null || knowledge.getJsonContent().isBlank()) {
            root = OBJECT_MAPPER.createObjectNode();
            root.put("tag", tag);
            root.put("projectDirectory", projectDirectory.toString());
            entries = root.putArray("entries");
        } else {
            root = parseKnowledgeObject(knowledge.getJsonContent(), tag, projectDirectory);
            final JsonNode existingEntries = root.get("entries");
            if (existingEntries instanceof final ArrayNode arrayNode) {
                entries = arrayNode;
            } else {
                entries = root.putArray("entries");
            }
        }

        final ObjectNode entryNode = entries.addObject();
        entryNode.put("timestamp", now.toString());
        entryNode.put("source", source.isBlank() ? "user_query" : source);
        if (!query.isBlank()) {
            entryNode.put("query", query);
        }
        entryNode.put("note", note);

        final String jsonContent;
        try {
            jsonContent = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (final IOException e) {
            return "Failed to serialize project knowledge for " + projectDirectory + ": " + e.getMessage();
        }

        if (knowledge == null) {
            knowledge = new ProjectKnowledge(null, project, tag, jsonContent, now, now);
            knowledge.persist();
        } else {
            knowledge.setJsonContent(jsonContent);
            knowledge.setUpdatedAt(now);
        }

        return "Cached project knowledge: project=" + projectDirectory
                + " tag=" + tag
                + " entries=" + entries.size();
    }

    public String todoLine(final String todo) {
        return "- [ ] " + todo + " (created " + LocalDate.now() + ")\n";
    }
}