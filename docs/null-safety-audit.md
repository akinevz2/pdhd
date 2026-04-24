# Null-Safety Audit and Fixes

**Branch**: null-safety  
**Session**: Comprehensive null-returning method audit and elimination

---

## Executive Summary

Identified and documented null-returning methods across the application. This audit tracks patterns where methods return `null` instead of raising exceptions or using Optional/empty collections. Goal: Eliminate unsafe null returns and improve error semantics.

> Note: the `RepoService`/`GitRepository`/`GithubRepository` findings below are historical. The current codebase uses `GithubMetadataService` together with the persisted `GitFolder` and `GithubMetadata` entities in `ac.uk.sussex.kn253.repository`; the documented `RepoService` API is no longer present.

---

## Null-Returning Methods Inventory

### 1. **RepoService** (Historical; superseded)

**File**: `src/main/java/ac/uk/sussex/kn253/services/RepoService.java`

#### Methods:

- `getGithubRepository(Path)` → `GithubRepository | null`
  - Returns null if `gh` CLI not installed, path not a git repo, or GitHub lookup fails
  - **Rationale**: GitHub metadata is optional enrichment
  - **Pattern**: Graceful degradation
  - **Risk**: Low - callers likely check for null

- `getGitRepository(Path)` → `GitRepository | null`
  - Returns null if path is not a git repository
  - **Rationale**: Returns null for "not a git repo" (not an error)
  - **Pattern**: Optional capability detection
  - **Risk**: Medium - calling code should handle null

- `parseGhRepoJson(String)` → `GithubRepository | null`
  - Returns null if "name" field is missing from JSON
  - **Rationale**: Malformed response handling
  - **Risk**: Low - private method, controlled caller

- `extractJsonStringField(String, String)` → `String | null`
  - Returns null if field not found in JSON
  - **Rationale**: Field extraction helper
  - **Risk**: Low - private helper, predictable null handling

---

### 2. **ToolService** (Null as error sentinel)

**File**: `src/main/java/ac/uk/sussex/kn253/services/ToolService.java`

#### Method:

- `classifyError(String)` → `String | null` (returns "NullResult")
  - **Line 174**: `return null;` → checks if `result == null` and classifies as "NullResult"
  - **Rationale**: Telemetry classification when result is null
  - **Risk**: Low - purely internal telemetry logic

---

### 3. **EmbeddingService** (Null for disabled/failed operations)

**File**: `src/main/java/ac/uk/sussex/kn253/services/EmbeddingService.java`

#### Methods:

- `generateEmbedding(String, String)` → `EmbeddingVector | null`
  - Returns null if service disabled, text blank, or generation fails
  - **Rationale**: Graceful degradation for optional embeddings
  - **Pattern**: Non-critical feature
  - **Risk**: Medium - some callers may not check null

- `search(String, int, String)` → `List<EmbeddingMatch> | empty`
  - Returns empty list, not null (safer pattern)
  - **Status**: Already correct

- `getRecentEmbeddings(String, int)` → `List<EmbeddingMatch> | empty`
  - Returns empty list, not null (safer pattern)
  - **Status**: Already correct

---

### 4. **ChatService** (Null handling in prompt lookups)

**File**: `src/main/java/ac/uk/sussex/kn253/services/ChatService.java`

#### Method:

- `directReply(String)` → `String | null`
  - Returns null if no direct-reply pattern matches
  - **Rationale**: Optional early-exit handler
  - **Risk**: Low - used in conditional upstream

---

### 5. **SystemPromptMenu** and **OllamaConfigMenu** (UI interaction helpers)

**File**: `src/main/java/ac/uk/sussex/kn253/services/SystemPromptMenu.java`  
**File**: `src/main/java/ac/uk/sussex/kn253/services/OllamaConfigMenu.java`

#### Pattern:

Multiple `return null` statements in JLine menu interaction code where user interaction may be cancelled/skipped.

**Rationale**: Menu operations are optional; null signals cancellation  
**Risk**: High - UI code frequently accesses results without null checks  
**Recommendation**: Use Optional<T> or throw CancellationException instead

---

### 6. **Tool Support Classes** (Null for error cases)

#### ExploreToolSupport

- Line 447: `return null;` in error path
- **Context**: Need to examine

#### IntrospectToolSupport

- Line 343: `return null;` in error path
- **Context**: Need to examine

---

## Risk Assessment

| Category                                      | Risk   | Count | Recommendation                                     |
| --------------------------------------------- | ------ | ----- | -------------------------------------------------- |
| Optional metadata (RepoService)               | Low    | 4     | Use Optional<T> or empty collections               |
| Telemetry (ToolService)                       | Low    | 1     | Already safe - only for classification             |
| Optional features (EmbeddingService)          | Medium | 1     | Already mostly safe with empty returns             |
| UI menus (SystemPromptMenu, OllamaConfigMenu) | High   | 10+   | Replace with Optional or exception-based semantics |
| Tool implementations                          | Medium | 2     | Examine context and document                       |

---

## Prioritized Fix Plan

### Phase 1: Low-hanging fruit (Optional metadata)

1. Replace `RepoService.getGithubRepository()` with `Optional<GithubRepository>`
2. Replace `RepoService.getGitRepository()` with `Optional<GitRepository>`
3. Update callers in ProjectDiscoveryService

### Phase 2: Menu interactions (UI layer)

4. Replace `SystemPromptMenu.promptForText()` and others with Optional or custom checked exception
5. Replace `OllamaConfigMenu` methods similarly
6. Update callers to handle new semantics

### Phase 3: Tool implementations

7. Examine ExploreToolSupport line 447
8. Examine IntrospectToolSupport line 343
9. Document rationale or refactor as needed

---

## Notes for Report

This audit demonstrates that null-returning methods are concentrated in:

1. **Optional capability detection** (RepoService) - legitimate use of null as "feature unavailable"
2. **UI interaction** (Menus) - problematic; Optional or exceptions are cleaner
3. **Graceful degradation** (EmbeddingService) - already mostly safe

The pattern suggests the codebase has thoughtful null handling for optional features, but could improve by using Optional<T> or custom exception types for UI flows where null currently signals user cancellation.
