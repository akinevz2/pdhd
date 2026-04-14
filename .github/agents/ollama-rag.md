# Coding Agent Specification: Quarkus LangChain4j RAG + Tool Calling

## Project Context

This is a Java + Quarkus application using LangChain4j for Ollama-backed LLM integration.
The LLM assistant uses Gemma4 for reasoning and Qwen3-Embedding for embeddings.
The database is SQLite via Panache (PanacheEntity / PanacheRepository).

---

## Core Philosophy

**Prefer simplicity over defensiveness.**
Do not generate null-check-heavy service layers, factory patterns, or deep inheritance hierarchies.
The LLM integration stack is inherently asynchronous and context-driven — the code should reflect that.

**Composition over abstraction.**
Wire components directly. Avoid introducing intermediate service classes unless they encapsulate
genuinely reusable business logic. A method that only delegates to a repository is not a service — it is noise.

**Trust Panache.**
PanacheEntity and PanacheRepository already provide the CRUD layer. Do not wrap them in additional
service classes unless there is domain logic that warrants it. Prefer static Panache methods on the entity
itself for simple queries.

---

## Database Layer Rules

### ProjectKnowledge Table

- `ProjectKnowledge` extends `PanacheEntity`
- Fields are typed — do not store arbitrary JSON blobs unless the schema genuinely requires it
- If a field is a structured list, use a `@Type` annotation or a proper `@ElementCollection` — not a raw JSON string parsed at runtime
- Queries belong on the entity as named static methods:

```java
// CORRECT
public class ProjectKnowledge extends PanacheEntity {
    public String projectPath;
    public String summary;
    public Instant lastCrawled;

    public static Optional<ProjectKnowledge> findByPath(String path) {
        return find("projectPath", path).firstResultOptional();
    }

    public static List<ProjectKnowledge> findStale(Instant threshold) {
        return list("lastCrawled < ?1", threshold);
    }
}
```

```java
// INCORRECT — do not do this
public class ProjectKnowledgeService {
    @Inject
    ProjectKnowledgeRepository repository;

    public Optional<ProjectKnowledge> findByPath(String path) {
        if (path == null) return Optional.empty(); // unnecessary null check
        return repository.findByPath(path); // pointless delegation
    }
}
```

### General Database Rules

- Transactions belong at the resource or AI service boundary, not buried in helper classes
- Use `@Transactional` on the method that owns the unit of work
- Do not create repository classes for entities that only need basic CRUD — use PanacheEntity static methods directly
- If a query is complex enough to warrant a repository, document why in a comment

---

## LangChain4j Integration Rules

### AI Service Definition

Define the AI service as a simple interface annotated with `@RegisterAiService`:

```java
@RegisterAiService(tools = {FileTools.class, FolderTools.class, SummarisationTools.class})
@ApplicationScoped
public interface ProjectAssistant {

    @SystemMessage("""
        You are a project knowledge assistant. You have access to tools that allow you
        to read files, list folder contents, and summarise collections of files.
        Always use tools to retrieve information rather than making assumptions.
        When summarising projects, call the summarisation subagent tool rather than
        attempting to summarise inline.
        """)
    String chat(@UserMessage String userMessage);
}
```

### Tool Classes

- Each tool class is a plain `@ApplicationScoped` CDI bean
- Tool methods are annotated with `@Tool` and a clear, descriptive `@P` parameter descriptions
- Tool methods must be short — they delegate to Panache or file system operations directly
- Do not inject other tool classes into tool classes — tools are flat, not hierarchical

```java
@ApplicationScoped
public class FileTools {

    @Tool("Read the contents of a file at the given absolute path")
    public String readFile(@P("Absolute path to the file") String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool("List all files in a folder at the given absolute path")
    public List<String> listFolder(@P("Absolute path to the folder") String path) {
        try (var stream = Files.list(Path.of(path))) {
            return stream.map(Path::toString).toList();
        } catch (IOException e) {
            return List.of("Error listing folder: " + e.getMessage());
        }
    }
}
```

### Subagent Tool Pattern

For summarisation subagents, define a separate AI service interface and call it from a tool:

```java
@RegisterAiService
@ApplicationScoped
public interface SummarisationSubagent {

    @SystemMessage("You are a summarisation agent. Summarise the provided file contents concisely.")
    String summarise(@UserMessage String combinedFileContents);
}

@ApplicationScoped
public class SummarisationTools {

    @Inject
    SummarisationSubagent subagent;

    @Inject
    FileTools fileTools;

    @Tool("Summarise a collection of files in a folder and cache the result in ProjectKnowledge")
    @Transactional
    public String summariseFolder(@P("Absolute path to the folder") String folderPath) {
        var files = fileTools.listFolder(folderPath);
        var combined = files.stream()
            .map(fileTools::readFile)
            .collect(Collectors.joining("\n\n---\n\n"));

        var summary = subagent.summarise(combined);

        var knowledge = ProjectKnowledge.findByPath(folderPath)
            .orElseGet(ProjectKnowledge::new);
        knowledge.projectPath = folderPath;
        knowledge.summary = summary;
        knowledge.lastCrawled = Instant.now();
        knowledge.persist();

        return summary;
    }
}
```

---

## RAG Rules

### Embedding and Retrieval

- Use `@Inject EmbeddingStore` and `@Inject EmbeddingModel` directly — do not wrap in a service
- Populate the embedding store at crawl time, not at query time
- Use `EmbeddingStoreContentRetriever` wired into the AI service via `@RegisterAiService(retriever = ...)`
- Do not implement custom retrieval logic unless the built-in retriever is genuinely insufficient

### ProjectKnowledge as RAG Cache

- After summarising a project, store the summary in both SQLite (for structured queries) and the embedding store (for semantic retrieval)
- The embedding store entry should include the project path as metadata for filtering
- Prefer re-using cached summaries over re-crawling — check `lastCrawled` before invoking the summarisation subagent

---

## What NOT to Generate

The following patterns are explicitly banned in this codebase:

- **Service classes that only delegate to repositories** — use Panache static methods directly
- **Null checks on injected CDI beans** — Quarkus CDI guarantees injection; null checks are noise
- **Optional.ofNullable() wrapping Panache results** — Panache already returns Optional where appropriate
- **Custom exception hierarchies** for LLM tool errors — return error strings to the model instead
- **Abstract base classes for tools** — tools are flat CDI beans, not class hierarchies
- **Manager, Handler, Helper, Util class names** — these are signals of poor decomposition
- **Logging every null check** — log at service boundaries only, not inside every guard clause
- **Thread.sleep() or manual retry logic** — use Quarkus fault tolerance annotations if needed

---

## application.properties Conventions

```properties
# Gemma4 for reasoning
quarkus.langchain4j.ollama.chat-model.model-id=gemma4
quarkus.langchain4j.ollama.chat-model.num-ctx=131072

# Qwen3 for embeddings
quarkus.langchain4j.ollama.embedding-model.model-id=qwen3-embedding

# SQLite
quarkus.datasource.db-kind=other
quarkus.datasource.jdbc.driver=org.sqlite.JDBC
quarkus.datasource.jdbc.url=jdbc:sqlite:./projectknowledge.db
quarkus.hibernate-orm.database.generation=update
```

---

## Summary of Correct Composition

```
HTTP Resource / CLI entrypoint
    └── ProjectAssistant (AI Service interface)
            ├── FileTools (CDI bean, @Tool methods)
            ├── FolderTools (CDI bean, @Tool methods)
            └── SummarisationTools (CDI bean, @Tool methods)
                    ├── SummarisationSubagent (AI Service interface)
                    ├── FileTools (injected)
                    └── ProjectKnowledge.persist() (direct Panache call)
```

No intermediate service layer. No repository wrappers. No null guards on CDI beans.
Tools are flat, composable, and directly wired to Panache and file system operations.
