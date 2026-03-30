# Tool Calling Conventions

## Overview

This document describes the conventions and patterns for defining, implementing, and invoking tools in the PDHD system. Tools provide the assistant with structured capabilities to interact with the project, filesystem, and external systems.

## Tool Lifecycle

### 1. Tool Definition

Tools are defined as `ToolMacro` subclasses with explicit contracts:

```java
public abstract class ToolMacro {
    public abstract String execute(ToolArguments args, String memoryId);
}
```

### 2. Tool Registration

Tools are grouped into `ToolMacroToolset` implementations:

```java
@Singleton
public class ExploreToolset extends ToolMacroToolset {
    @Override
    protected List<Class<? extends ToolMacro>> toolMacros() {
        return List.of(
            ChangeWorkingDirectoryTool.class,
            ListFilesTool.class,
            ReadFileTool.class,
            // ... other tools
        );
    }
}
```

### 3. Tool Discovery

Tools are discovered automatically:

```
Application Startup
    ↓
ToolService initialized with list of ToolModule instances
    ↓
Each ToolModule instantiated and registered
    ↓
ToolMacroRegistry built from all ToolMacro classes
```

### 4. Tool Invocation

Tools are invoked via `ToolService.execute()`:

```java
ToolExecutionRequest request = new ToolExecutionRequest(
    "read_file",
    Map.of("filePath", "src/Main.java")
);
String result = toolService.execute(request, memoryId);
```

## Tool Structure

### Minimal Tool Implementation

```java
public class ExampleTool extends ToolMacro {

    @Override
    public String execute(ToolArguments args, String memoryId) {
        // Parse arguments
        String param1 = args.getString("param1");

        // Validate
        if (param1 == null || param1.isBlank()) {
            return "Error: param1 is required";
        }

        // Execute
        try {
            String result = doSomething(param1);
            return result;
        } catch (Exception e) {
            return "Tool execution failed: " + e.getMessage();
        }
    }

    private String doSomething(String param1) {
        // Implementation
        return "Success";
    }
}
```

### Tool with Dependencies

Tools can inject services via constructor:

```java
public class AdvancedTool extends ToolMacro {

    private final WorkingDirectoryService workingDirectoryService;
    private final FileService fileService;

    public AdvancedTool(
        WorkingDirectoryService workingDirectoryService,
        FileService fileService
    ) {
        this.workingDirectoryService = workingDirectoryService;
        this.fileService = fileService;
    }

    @Override
    public String execute(ToolArguments args, String memoryId) {
        // Use injected services
        String cwd = workingDirectoryService.getCwd();
        // ...
    }
}
```

## Tool Contracts

### Argument Format

Tools receive arguments as a `ToolArguments` wrapper around a Map:

```java
public interface ToolArguments {
    String getString(String key);
    Integer getInteger(String key);
    Boolean getBoolean(String key);
    <T> T get(String key, Class<T> type);

    static ToolArguments parse(Map<String, Object> map) { ... }
}
```

Usage:

```java
String filePath = args.getString("filePath");         // Required key
Integer limit = args.getInteger("limit");              // Integer parsing
Boolean recursive = args.getBoolean("recursive");      // Boolean parsing
```

### Result Format

Tools always return a **String result**, which should:

1. **Be informative** - Provide clear information or ask for clarification
2. **Be concise** - Keep within reasonable bounds (typically < 10KB)
3. **Be LLM-readable** - Format for easy parsing by the model (e.g., JSON, markdown, structured text)
4. **Handle errors gracefully** - Return error messages instead of throwing exceptions

Examples:

```java
// Success case
return "File contents:\n" + fileContents;

// Error case
return "Error: File not found: " + filePath;

// Structured result
return Json.createObjectBuilder()
    .add("status", "success")
    .add("count", 42)
    .add("items", Json.createArrayBuilder()
        .add("item1")
        .add("item2")
        .build())
    .build()
    .toString();
```

### Transaction Semantics

Tool execution is transactional:

```
ToolService.execute(request, memoryId)
    ↓ [Transactional boundary]
    ├─ ToolModule.execute()
    ├─ Tool implementation runs
    ├─ Side effects (DB writes, file changes)
    └─ All committed or rolled back atomically
```

**Implication:** If a tool fails after writing to the database, both changes are rolled back. This ensures consistency but means:

- Don't rely on partial side effects
- Tool results are the source of truth, not intermediate state
- Re-running a failed tool should be safe

### Memory ID

The `memoryId` parameter is passed to every tool for context:

```java
@Override
public String execute(ToolArguments args, String memoryId) {
    // memoryId typically identifies the current conversation/session
    // Use for:
    // - Caching read contexts within a session
    // - Associating tool results with a user interaction
    // - Scoping tool-specific data
}
```

## Tool Categories

### Explore Toolset

**Purpose:** Filesystem navigation and discovery

**Tools:**

- `get_current_working_directory` - Return current working directory
- `change_working_directory` - Change working directory
- `list_files_recursive` - Recursively list files
- `search_paths` - Search for paths matching patterns
- `analyze_path_detailed` - Detailed analysis of a path
- `summarize_path` - Quick summary of a path

**Key Characteristics:**

- Read-only (no state changes to files)
- Defensive against traversal attacks
- Respects `.gitignore` and ignore rules
- Includes sampling/truncation for large directories

### Read Toolset

**Purpose:** File content retrieval

**Tools:**

- `read_file` - Read file contents with line limits

**Key Characteristics:**

- Transactional with caching side effect
- Line-range support for large files
- UTF-8 text file focus (binary files handled specially)
- Results cached in `ProjectKnowledge` for reuse

### Write Toolset

**Purpose:** Controlled output generation

**Tools:**

- `write_file` - Create or overwrite files
- `create_report` - Generate structured reports
- `create_timeline` - Generate timeline documents
- `create_plan` - Generate project plans
- `append_project_todo` - Append to project TODO list
- `cache_project_knowledge` - Explicitly cache project knowledge

**Key Characteristics:**

- Integration with file persistence
- Scoped to project directory
- Results recorded in `ToolActivityService`
- Some tools (create_report, create_timeline) use templating

### Introspect Toolset

**Purpose:** Session and project introspection

**Tools:**

- `read_folder_manifest` - Manifest of folder contents with sampling
- `read_project_manifest` - Manifest of project structure
- `read_project_knowledge` - Retrieve cached project knowledge
- `get_session_context` - Current CWD + recent tool history
- `open_workspace_canvas` - Workspace information

**Key Characteristics:**

- Provide context-aware information
- Respect read cache for fast retrieval
- Enable loop detection (via session context)
- Used for introspection and debugging

## Adding a New Tool

### Step 1: Create Tool Class

```java
public class MyNewTool extends ToolMacro {

    @Inject
    private MyService myService;

    @Override
    public String execute(ToolArguments args, String memoryId) {
        // Implementation
        return "Result";
    }
}
```

### Step 2: Add to ToolMacroRegistry

Uncomment or add to `ToolMacroRegistry.ALL`:

```java
private static final List<Class<? extends ToolMacro>> ALL = List.of(
    // ... existing tools ...
    MyNewTool.class,  // ADD HERE
    // ... more tools ...
);
```

### Step 3: Define Tool Specification (for LLM)

Tools are discovered via reflection, but LLMs need explicit specifications. This is handled by `ToolMacroRegistry`:

```java
public List<dev.langchain4j.agent.tool.ToolSpecification> specifications() {
    return macros.stream()
        .map(this::toSpecification)
        .toList();
}
```

The reflection-based discovery extracts:

- Tool name (from class name, converted to snake_case)
- Tool description (from JavaDoc or @Description annotation)
- Parameter descriptions (from JavaDoc or @Parameter annotations)

### Step 4: Write Tests

```java
public class MyNewToolTest {

    @Test
    public void testBasicExecution() {
        MyNewTool tool = new MyNewTool();
        ToolArguments args = ToolArguments.parse(
            Map.of("param1", "value1")
        );
        String result = tool.execute(args, "test-memory-id");

        assertThat(result).contains("expected content");
    }

    @Test
    public void testErrorHandling() {
        MyNewTool tool = new MyNewTool();
        ToolArguments args = ToolArguments.parse(new HashMap<>());
        String result = tool.execute(args, "test-memory-id");

        assertThat(result).contains("Error");
    }
}
```

### Step 5: Document

Add tool documentation to this file or a separate tools reference:

```markdown
### my_new_tool

**Description:** What this tool does

**Arguments:**

- `param1` (string, required) - Description of param1
- `param2` (integer, optional) - Description of param2

**Returns:** Description of return value

**Example:**
```

## Tool Naming Conventions

### Canonical Names

Tools are invoked by **canonical names** in **snake_case**:

```
ChangeWorkingDirectory → change_working_directory
ListFilesRecursive → list_files_recursive
ReadFile → read_file
```

Conversion: `ThisToolName` → `this_tool_name`

### Aliases

Tools can define aliases for backward compatibility:

```java
public class ReadFileTool extends ToolMacro {
    // Canonical: read_file
    // Aliases: read, load, fetch-file, fetch_file
}
```

Resolution:

1. Exact match on canonical name
2. Case-insensitive match lowercase/trimmed
3. No partial matching; exact string match only

## Error Handling Patterns

### Argument Validation

```java
String requiredParam = args.getString("requiredParam");
if (requiredParam == null || requiredParam.isBlank()) {
    return "Error: requiredParam is required";
}

Integer optionalLimit = args.getInteger("limit");
int limit = optionalLimit != null ? optionalLimit : DEFAULT_LIMIT;
```

### Common Error Messages

```java
// Missing argument
"Error: 'filePath' argument is required"

// Invalid value
"Error: 'filePath' does not exist or is not readable"

// Permission denied
"Error: Access denied to 'filePath'"

// Tool loop detected
"Error: Tool loop detected - repeated identical calls"

// Resource exhausted
"Error: Result too large (exceeds limit of 100 lines)"
```

## Observability and Debugging

### Tool Activity Tracking

Every tool execution is recorded:

```
ToolActivityService.recordToolExecution(
    toolName,
    args,
    result,
    requestedFilePaths,
    memoryId
)
```

Access recent activity:

```bash
curl http://localhost:8080/api/tool-activity?limit=50
```

### Logging Best Practices

Use `org.jboss.logging.Logger`:

```java
private static final Logger LOG = Logger.getLogger(MyTool.class);

@Override
public String execute(ToolArguments args, String memoryId) {
    LOG.infof("Executing MyTool with args: %s", args);
    try {
        // Implementation
        LOG.infof("MyTool completed successfully");
        return result;
    } catch (Exception e) {
        LOG.warnf(e, "MyTool failed: %s", e.getMessage());
        return "Error: " + e.getMessage();
    }
}
```

### Testing Tool Execution

```java
@Test
public void testToolViaService() {
    ToolService toolService = // ... get instance
    ToolExecutionRequest request = new ToolExecutionRequest(
        "my_new_tool",
        Map.of("param1", "value1")
    );
    String result = toolService.execute(request, "test-id");
    assertThat(result).isNotEmpty();
}
```

## Performance Considerations

### Tool Timeout

Tools are expected to complete within reasonable time (typically < 30s):

```java
// If operation might be slow, provide progress indication
@Override
public String execute(ToolArguments args, String memoryId) {
    // Provide streaming or chunked results for long operations
    // Or break into multiple tool calls
}
```

### Result Size

Tool results should be bounded:

```java
// Bad: Return entire 10MB file
String contents = Files.readString(path);
return contents;  // ← Too large!

// Good: Limit result or chunk
List<String> lines = Files.readAllLines(path);
List<String> limited = lines.stream()
    .limit(maxLineLimit)
    .toList();
return String.join("\n", limited) +
    (lines.size() > maxLineLimit ? "\n... (truncated)" : "");
```

### Caching and Efficiency

Use caching where appropriate:

```java
// Read tool caches results in ProjectKnowledge
// Introspect tools retrieve known data efficiently
// Avoid redundant file I/O
```

## Tool Versioning

### Backward Compatibility

When modifying tools:

1. **Adding optional parameters:** Always backward compatible
2. **Changing parameter names:** Create aliases for old names
3. **Changing return format:** Provide wrapper or dual output
4. **Removing tools:** Deprecate first, provide migration path

Example:

```java
@Override
public String execute(ToolArguments args, String memoryId) {
    // Support old parameter name 'path' and new name 'filePath'
    String filePath = args.getString("filePath");
    if (filePath == null) {
        filePath = args.getString("path");  // Backward compat
    }
    // ...
}
```

## Integration with Embeddings

When embeddings are enabled, tools can leverage embedding functionality:

1. **Retrieve semantically similar content:**

   ```java
   // Within a tool, query the embedding service
   List<ContentWithEmbedding> similar = embeddingService.search(
       userQuery,
       limit
   );
   ```

2. **Cache embeddings of read results:**

   ```java
   // ReadToolSupport automatically embeds file contents
   // for later semantic retrieval
   ```

3. **Tool loops and embedding context:**
   ```java
   // Introspect tool can return recent embeddings
   // to give LLM awareness of what content was recently retrieved
   ```

See [embeddings.md](embeddings.md) for complete embedding integration guide.

## Common Tool Patterns

### Request-Response Pattern

Simplest pattern: take input, return output, no state:

```java
public String execute(ToolArguments args, String memoryId) {
    String input = args.getString("input");
    String output = process(input);
    return output;
}
```

### Discovery Pattern

Explore structure and return metadata:

```java
public String execute(ToolArguments args, String memoryId) {
    String path = args.getString("path");
    List<Path> results = discoverItems(path);
    return formatResults(results);  // JSON or markdown
}
```

### Side-Effect Pattern

Modify state and return confirmation:

```java
public String execute(ToolArguments args, String memoryId) {
    String filePath = args.getString("filePath");
    String contents = args.getString("contents");
    Files.writeString(Path.of(filePath), contents);
    return "Successfully wrote to: " + filePath;
}
```

### Context-Aware Pattern

Use memoryId and session context:

```java
public String execute(ToolArguments args, String memoryId) {
    // Use memoryId to access session-specific data
    ProjectKnowledge knowledge = getKnowledgeForSession(memoryId);
    // Build response using context
    String result = buildContextAwareResponse(args, knowledge);
    return result;
}
```

## Best Practices

1. **Always validate input** - Check for null, empty, invalid values
2. **Provide clear error messages** - Help the LLM understand what went wrong
3. **Keep results concise** - Trim/sample large outputs
4. **Document via JavaDoc** - Used for LLM specifications
5. **Test edge cases** - Null inputs, missing files, permissions
6. **Log important events** - For debugging and monitoring
7. **Handle timeouts gracefully** - Don't let long operations hang
8. **Respect security boundaries** - No directory traversal or privilege escalation
9. **Cache expensive operations** - Use ProjectKnowledge for read results
10. **Follow naming conventions** - Consistency aids discoverability

## References

- [Tool Calling Architecture](tool-calling-architecture.md) - Architectural overview
- [Chat Service](chat-service.md) - How tools are invoked during conversations
- [Embeddings Guide](embeddings.md) - Integrating embeddings with tools
